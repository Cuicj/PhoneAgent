package com.example.baseapp.data;

import android.content.Context;
import android.hardware.ConsumerIrManager;

/**
 * 红外硬件能力检测：只以系统服务与 hasIrEmitter() 为准，不做任何能力猜测。
 */
public final class IrCapability {

    private IrCapability() {
    }

    /** 本机是否存在可用的红外发射器 */
    public static boolean hasEmitter(Context context) {
        ConsumerIrManager manager = (ConsumerIrManager) context.getApplicationContext()
                .getSystemService(Context.CONSUMER_IR_SERVICE);
        if (manager == null) {
            return false;
        }
        try {
            return manager.hasIrEmitter();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
