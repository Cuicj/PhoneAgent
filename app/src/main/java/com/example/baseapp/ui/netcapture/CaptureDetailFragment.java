package com.example.baseapp.ui.netcapture;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.baseapp.R;
import com.example.baseapp.data.netcapture.CaptureRecord;
import com.example.baseapp.data.netcapture.CaptureStore;
import com.example.baseapp.databinding.FragmentCaptureDetailBinding;
import com.example.baseapp.util.Formats;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 抓取详情：分「请求」「响应」「元数据」三段。
 *
 * <p>HTTP 展示完整头部与可读正文；HTTPS 只展示连接元数据，并明确标注内容已加密无法解密。</p>
 */
public class CaptureDetailFragment extends Fragment {

    private static final long REFRESH_INTERVAL_MS = 700L;

    private FragmentCaptureDetailBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
    private long recordId;

    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            if (binding == null) {
                return;
            }
            render();
            CaptureRecord record = CaptureStore.get().find(recordId);
            // 记录结束后停止轮询，避免无意义的刷新
            if (record == null || !record.finished) {
                handler.postDelayed(this, REFRESH_INTERVAL_MS);
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCaptureDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recordId = requireArguments().getLong("recordId", -1L);
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.title_capture_detail);
        handler.removeCallbacksAndMessages(null);
        handler.post(refreshTask);
    }

    @Override
    public void onPause() {
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroyView();
        binding = null;
    }

    /** 按当前记录内容刷新三个分区 */
    private void render() {
        CaptureRecord record = CaptureStore.get().find(recordId);
        if (record == null) {
            binding.summaryTitle.setText(R.string.capture_record_gone);
            binding.summaryText.setText(R.string.capture_record_gone_desc);
            binding.requestMeta.setText(R.string.capture_record_gone_desc);
            binding.responseMeta.setText(R.string.capture_record_gone_desc);
            binding.metadataText.setText(R.string.capture_record_gone_desc);
            hide(binding.requestHeaders);
            hide(binding.requestBody);
            hide(binding.requestNote);
            hide(binding.responseHeaders);
            hide(binding.responseBody);
            hide(binding.responseNote);
            return;
        }

        binding.summaryTitle.setText(record.displayTarget());
        binding.summaryText.setText(getString(R.string.capture_detail_summary_text,
                protocolLabel(record),
                record.displayTarget(),
                stateLabel(record),
                timeFormat.format(new Date(record.startTime)),
                record.durationMs(),
                Formats.formatBytes(record.upBytes),
                Formats.formatBytes(record.downBytes)));

        renderRequest(record);
        renderResponse(record);
        binding.metadataText.setText(buildMetadata(record));
    }

    private void renderRequest(CaptureRecord record) {
        if (record.isEncrypted()) {
            // 不伪装能看到内容：HTTPS 的请求部分只说明加密
            binding.requestMeta.setText(R.string.capture_detail_encrypted);
            hide(binding.requestHeaders);
            hide(binding.requestBody);
            hide(binding.requestNote);
            return;
        }
        String method = record.method == null ? "—" : record.method;
        String path = record.path == null ? "—" : record.path;
        binding.requestMeta.setText(getString(R.string.capture_detail_request_line, method, path));

        setTextOrGone(binding.requestHeaders, record.requestHeaders);
        if (record.requestBodyReadable) {
            setTextOrGone(binding.requestBody, record.requestBody);
            hide(binding.requestNote);
        } else {
            hide(binding.requestBody);
            binding.requestNote.setText(R.string.capture_detail_body_unreadable);
            binding.requestNote.setVisibility(View.VISIBLE);
        }
    }

    private void renderResponse(CaptureRecord record) {
        if (record.isEncrypted()) {
            binding.responseMeta.setText(getString(R.string.capture_detail_https_response,
                    tlsLabel(record)));
            hide(binding.responseHeaders);
            hide(binding.responseBody);
            hide(binding.responseNote);
            return;
        }
        String status = record.statusCode > 0
                ? String.valueOf(record.statusCode) : getString(R.string.capture_detail_status_unknown);
        binding.responseMeta.setText(getString(R.string.capture_detail_response_status, status));

        setTextOrGone(binding.responseHeaders, record.responseHeaders);
        if (record.responseBodyReadable) {
            setTextOrGone(binding.responseBody, record.responseBody);
            hide(binding.responseNote);
        } else {
            hide(binding.responseBody);
            binding.responseNote.setText(R.string.capture_detail_body_unreadable);
            binding.responseNote.setVisibility(View.VISIBLE);
        }
    }

    /** 元数据段落：协议、端点、时间、耗时、流量、TLS 与说明 */
    private String buildMetadata(CaptureRecord record) {
        return getString(R.string.capture_detail_metadata_text,
                protocolLabel(record),
                record.remoteIp,
                record.remotePort,
                timeFormat.format(new Date(record.startTime)),
                record.durationMs(),
                Formats.formatBytes(record.upBytes),
                Formats.formatBytes(record.downBytes),
                tlsLabel(record),
                stateLabel(record),
                record.note == null ? "—" : record.note);
    }

    private String protocolLabel(CaptureRecord record) {
        switch (record.protocol) {
            case CaptureRecord.PROTOCOL_HTTP:
                return getString(R.string.capture_protocol_http);
            case CaptureRecord.PROTOCOL_HTTPS:
                return getString(R.string.capture_protocol_https);
            case CaptureRecord.PROTOCOL_UDP:
                return getString(R.string.capture_protocol_udp);
            default:
                return getString(R.string.capture_protocol_tcp);
        }
    }

    private String stateLabel(CaptureRecord record) {
        if (!record.finished) {
            return getString(R.string.capture_in_progress);
        }
        return record.failed ? getString(R.string.capture_status_failed)
                : getString(R.string.capture_status_done);
    }

    private String tlsLabel(CaptureRecord record) {
        if (record.tlsState == null) {
            return getString(R.string.capture_tls_not_applicable);
        }
        return record.tlsState;
    }

    private static void setTextOrGone(TextView view, String text) {
        if (text == null || text.isEmpty()) {
            view.setVisibility(View.GONE);
        } else {
            view.setText(text);
            view.setVisibility(View.VISIBLE);
        }
    }

    private static void hide(View view) {
        view.setVisibility(View.GONE);
    }
}
