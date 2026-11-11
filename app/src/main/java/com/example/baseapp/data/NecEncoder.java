package com.example.baseapp.data;

/**
 * NEC 红外编码生成器：输出 ConsumerIrManager.transmit() 所需的微秒时序数组。
 */
public final class NecEncoder {

    /** NEC 标准载波频率 38 kHz */
    public static final int DEFAULT_FREQUENCY = 38000;

    private static final int HEADER_MARK = 9000;
    private static final int HEADER_SPACE = 4500;
    private static final int BIT_MARK = 560;
    private static final int ZERO_SPACE = 560;
    private static final int ONE_SPACE = 1690;
    private static final int TAIL_MARK = 560;

    private NecEncoder() {
    }

    /**
     * 生成 32 位 NEC 时序。
     * 地址码 ≤ 0xFF 时发送「地址 + 反码」；超过 0xFF 时按扩展 NEC 发送高低字节，不发送反码。
     */
    public static int[] encode(int address, int command) {
        int lowAddress = address & 0xFF;
        int highAddress = (address >> 8) & 0xFF;
        int commandByte = command & 0xFF;
        int[] bytes = address <= 0xFF
                ? new int[]{lowAddress, (~lowAddress) & 0xFF, commandByte, (~commandByte) & 0xFF}
                : new int[]{lowAddress, highAddress, commandByte, (~commandByte) & 0xFF};

        // 头段 2 + 32 位 × 2 + 尾段 1
        int[] pattern = new int[2 + bytes.length * 8 * 2 + 1];
        int index = 0;
        pattern[index++] = HEADER_MARK;
        pattern[index++] = HEADER_SPACE;
        for (int value : bytes) {
            for (int bit = 0; bit < 8; bit++) {
                pattern[index++] = BIT_MARK;
                // NEC 按低位先发
                pattern[index++] = ((value >> bit) & 1) == 1 ? ONE_SPACE : ZERO_SPACE;
            }
        }
        pattern[index] = TAIL_MARK;
        return pattern;
    }
}
