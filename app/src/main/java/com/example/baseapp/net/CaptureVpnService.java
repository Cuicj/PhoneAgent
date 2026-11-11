package com.example.baseapp.net;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import androidx.core.app.NotificationCompat;

import com.example.baseapp.MainActivity;
import com.example.baseapp.R;
import com.example.baseapp.data.netcapture.CaptureState;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * 网络请求抓取服务：建立本地 tun，把设备流量引入用户态转发并生成记录。
 *
 * <p>限制说明：仅转发 IPv4 的 TCP/UDP；HTTPS/QUIC 无法解密，只记录连接元数据。
 * 停止服务或系统收回 VPN 授权时会关闭 tun 与全部转发 Socket，避免 VPN 图标常驻。</p>
 */
public class CaptureVpnService extends VpnService {

    /** 启动抓取 */
    public static final String ACTION_START = "com.example.baseapp.net.capture.START";
    /** 停止抓取 */
    public static final String ACTION_STOP = "com.example.baseapp.net.capture.STOP";

    private static final String CHANNEL_ID = "net_capture_service";
    private static final int NOTIFICATION_ID = 2001;

    private ParcelFileDescriptor tunInterface;
    private FileInputStream tunInput;
    private FileOutputStream tunOutput;
    private Thread readerThread;

    private final TcpRelay tcpRelay = new TcpRelay(this);
    private final UdpRelay udpRelay = new UdpRelay(this);

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopCapture();
            return START_NOT_STICKY;
        }
        try {
            startForeground(NOTIFICATION_ID, buildNotification());
        } catch (RuntimeException e) {
            // 前台服务启动受限时直接反馈给界面，不留下常驻状态
            CaptureState.running = false;
            CaptureState.lastError = "无法启动前台服务：" + describe(e);
            stopSelf();
            return START_NOT_STICKY;
        }
        startCapture();
        return START_STICKY;
    }

    /** 建立 tun 并启动读取线程 */
    private synchronized void startCapture() {
        if (CaptureState.running) {
            return;
        }
        CaptureState.lastError = null;
        try {
            Builder builder = new Builder();
            builder.setSession(getString(R.string.app_name) + " 网络抓取");
            builder.addAddress("10.8.0.2", 24);
            builder.addRoute("0.0.0.0", 0);
            builder.setMtu(1500);
            // 使用当前网络的 IPv4 DNS，既保证解析可用，又能让 DNS 查询经过 tun 被记录
            for (InetAddress dns : currentDnsServers()) {
                builder.addDnsServer(dns);
            }
            // 本应用自身流量不进入 tun，避免代理 Socket 之外的请求形成环路
            try {
                builder.addDisallowedApplication(getPackageName());
            } catch (Exception ignored) {
                // 低版本或异常情况下忽略，代理 Socket 仍通过 protect 绕过
            }
            tunInterface = builder.establish();
            if (tunInterface == null) {
                failStart("VPN 建立失败：系统未返回 tun 接口，可能已有其他 VPN 正在运行");
                return;
            }
            tunInput = new FileInputStream(tunInterface.getFileDescriptor());
            tunOutput = new FileOutputStream(tunInterface.getFileDescriptor());
            CaptureState.running = true;
            CaptureState.startedAt = System.currentTimeMillis();
            readerThread = new Thread(() -> readLoop(tunInput), "capture-tun-reader");
            readerThread.setDaemon(true);
            readerThread.start();
        } catch (Exception e) {
            failStart("VPN 启动失败：" + describe(e));
        }
    }

    /** 持续读取 tun 中的 IP 包并分发到 TCP/UDP 中继 */
    private void readLoop(FileInputStream input) {
        byte[] buffer = new byte[32767];
        while (CaptureState.running && input != null) {
            int length;
            try {
                length = input.read(buffer);
            } catch (IOException e) {
                if (CaptureState.running) {
                    CaptureState.lastError = "读取 tun 失败：" + describe(e);
                }
                break;
            }
            if (length <= 0) {
                continue;
            }
            dispatch(buffer, length);
        }
        // 只有当前 tun 仍是本线程读取的那一个时才做清理，
        // 避免停止后立即重启时旧线程误关新 tun
        synchronized (CaptureVpnService.this) {
            if (tunInput != input) {
                return;
            }
            if (CaptureState.running) {
                CaptureState.running = false;
                if (CaptureState.lastError == null) {
                    CaptureState.lastError = "转发线程已停止";
                }
            }
            closeResources();
        }
        stopSelf();
    }

    /** 按 IP 头协议号分发；单包异常不影响整体转发 */
    private void dispatch(byte[] packet, int length) {
        if (length < PacketUtils.IP_HEADER_LEN) {
            return;
        }
        int version = (packet[0] & 0xF0) >> 4;
        if (version != 4) {
            return;
        }
        int headerLength = (packet[0] & 0x0F) * 4;
        if (headerLength < PacketUtils.IP_HEADER_LEN || headerLength > length) {
            return;
        }
        int protocol = packet[9] & 0xFF;
        try {
            if (protocol == 6) {
                tcpRelay.handle(packet, length, headerLength);
            } else if (protocol == 17) {
                udpRelay.handle(packet, length, headerLength);
            }
        } catch (Exception ignored) {
            // 丢弃异常报文
        }
    }

    /** 读取当前网络的 IPv4 DNS 服务器；读不到时返回空列表 */
    private List<InetAddress> currentDnsServers() {
        List<InetAddress> servers = new ArrayList<>();
        try {
            ConnectivityManager manager = getSystemService(ConnectivityManager.class);
            if (manager == null) {
                return servers;
            }
            Network network = manager.getActiveNetwork();
            LinkProperties properties = network == null ? null : manager.getLinkProperties(network);
            if (properties == null) {
                return servers;
            }
            for (InetAddress address : properties.getDnsServers()) {
                // 只转发 IPv4；IPv6 DNS 不在本工具覆盖范围内
                if (address instanceof Inet4Address) {
                    servers.add(address);
                }
            }
        } catch (Exception ignored) {
            // 读取失败时退化为不设置 DNS，系统会沿用底层网络解析
        }
        return servers;
    }

    /** 由转发线程写入一个回程 IP 包，写 tun 需要串行化 */
    synchronized void writeToTun(byte[] packet, int length) {
        FileOutputStream output = tunOutput;
        if (output == null) {
            return;
        }
        try {
            output.write(packet, 0, length);
        } catch (IOException ignored) {
            // tun 已关闭时写入失败属于正常停止流程
        }
    }

    /** 主动停止：释放资源并移除前台通知 */
    private void stopCapture() {
        CaptureState.running = false;
        closeResources();
        stopForeground(true);
        stopSelf();
    }

    private void failStart(String message) {
        CaptureState.running = false;
        CaptureState.lastError = message;
        closeResources();
        stopForeground(true);
        stopSelf();
    }

    /** 关闭 tun 与全部转发资源（幂等） */
    private synchronized void closeResources() {
        tcpRelay.closeAll();
        udpRelay.closeAll();
        if (tunInput != null) {
            try {
                tunInput.close();
            } catch (IOException ignored) {
            }
            tunInput = null;
        }
        if (tunOutput != null) {
            try {
                tunOutput.close();
            } catch (IOException ignored) {
            }
            tunOutput = null;
        }
        if (tunInterface != null) {
            try {
                tunInterface.close();
            } catch (IOException ignored) {
            }
            tunInterface = null;
        }
        readerThread = null;
    }

    @Override
    public void onRevoke() {
        // 系统或其他 VPN 应用收回授权：立即释放，避免抓取状态与界面不一致
        CaptureState.running = false;
        CaptureState.lastError = "VPN 授权已被收回，抓取已停止";
        closeResources();
        stopForeground(true);
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        CaptureState.running = false;
        closeResources();
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.capture_channel_name), NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.capture_notification_title))
                .setContentText(getString(R.string.capture_notification_text))
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .build();
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return message == null || message.isEmpty() ? e.getClass().getSimpleName() : message;
    }
}
