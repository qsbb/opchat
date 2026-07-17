package com.opchat.ime;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.win32.StdCallLibrary;

/**
 * 输入法冲突修复：参考 IMBlocker 的双模切换原理。
 *
 * 核心机制（不解除窗口 IME 关联，只切换输入法打开状态）：
 *  - 进入游戏内（非文本输入屏幕）：调用 ImmSetOpenStatus(himc, false) 关闭输入法打开状态，
 *    使输入法不再拦截键盘事件，但保留窗口的 IME 上下文关联。
 *  - 进入聊天框 / 命令输入框等文本输入屏幕：调用 ImmSetOpenStatus(himc, true) 重新打开输入法，
 *    因为 IME 上下文关联从未被解除，所以可以立即恢复输入功能。
 *
 * 这种方式比 ImmAssociateContextEx(hwnd, null, 0) 更可靠，
 * 后者会彻底断开 IME 关联，恢复时需要重新关联并可能丢失输入法状态。
 *
 * 仅在 Windows 平台生效，其他平台静默跳过。
 */
public final class IMEBlocker {
    private static final boolean WINDOWS;
    private static final Imm32 IMM32;
    private static final User32 USER32;

    static {
        String os = System.getProperty("os.name", "");
        WINDOWS = os.toLowerCase().contains("win");
        Imm32 imm = null;
        User32 user = null;
        if (WINDOWS) {
            try {
                imm = Native.load("imm32", Imm32.class);
                user = User32.INSTANCE;
            } catch (Throwable t) {
                imm = null;
            }
        }
        IMM32 = imm;
        USER32 = user;
    }

    /** IMM（输入法管理器）接口 */
    public interface Imm32 extends StdCallLibrary {
        Pointer ImmGetContext(WinDef.HWND hwnd);

        boolean ImmReleaseContext(WinDef.HWND hwnd, Pointer himc);

        boolean ImmSetOpenStatus(Pointer himc, boolean open);

        boolean ImmGetOpenStatus(Pointer himc);
    }

    private IMEBlocker() {}

    /** 当前平台是否支持 IME 控制 */
    public static boolean isSupported() {
        return WINDOWS && IMM32 != null && USER32 != null;
    }

    /**
     * 禁用输入法：关闭当前前台窗口的输入法打开状态。
     * 保留 IME 上下文关联，便于后续快速恢复。
     */
    public static void disableIME() {
        if (!isSupported()) return;
        try {
            WinDef.HWND hwnd = USER32.GetForegroundWindow();
            if (hwnd == null) return;
            Pointer himc = IMM32.ImmGetContext(hwnd);
            if (himc == null) return;
            try {
                // 关闭输入法打开状态，按键不再被输入法拦截
                IMM32.ImmSetOpenStatus(himc, false);
            } finally {
                IMM32.ImmReleaseContext(hwnd, himc);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 启用输入法：重新打开当前前台窗口的输入法状态。
     * 因为 IME 上下文关联从未被断开，所以可以立即恢复输入。
     */
    public static void enableIME() {
        if (!isSupported()) return;
        try {
            WinDef.HWND hwnd = USER32.GetForegroundWindow();
            if (hwnd == null) return;
            Pointer himc = IMM32.ImmGetContext(hwnd);
            if (himc == null) return;
            try {
                IMM32.ImmSetOpenStatus(himc, true);
            } finally {
                IMM32.ImmReleaseContext(hwnd, himc);
            }
        } catch (Throwable ignored) {}
    }
}
