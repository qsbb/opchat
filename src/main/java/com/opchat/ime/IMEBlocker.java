package com.opchat.ime;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.win32.StdCallLibrary;

/**
 * 输入法冲突修复：参考 IMBlocker 的原理，通过 Windows IMM API 控制输入法状态。
 *
 * 核心机制：
 *  - 进入游戏内（非文本输入屏幕）时，将当前窗口的 IME 上下文解除关联，
 *    使输入法不再拦截键盘事件（避免中文/日文/韩文输入法吞掉 WASD 等按键）。
 *  - 进入聊天框 / 命令输入框等文本输入屏幕时，恢复默认 IME 关联，允许正常输入。
 *
 * 仅在 Windows 平台生效，其他平台静默跳过。
 */
public final class IMEBlocker {
    private static final boolean WINDOWS;
    private static final Imm32 IMM32;
    private static final User32 USER32;

    // ImmAssociateContextEx 的标志位
    private static final int IACE_DEFAULT = 0x0001;       // 恢复默认 IME 关联

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

        boolean ImmAssociateContextEx(WinDef.HWND hwnd, Pointer himc, int flags);

        boolean ImmSetOpenStatus(Pointer himc, boolean open);
    }

    private IMEBlocker() {}

    /** 当前平台是否支持 IME 控制 */
    public static boolean isSupported() {
        return WINDOWS && IMM32 != null && USER32 != null;
    }

    /** 禁用输入法：解除当前前台窗口的 IME 关联 */
    public static void disableIME() {
        if (!isSupported()) return;
        try {
            WinDef.HWND hwnd = USER32.GetForegroundWindow();
            if (hwnd == null) return;
            // himc 传 null + flags=0 表示解除关联，使 IME 收不到按键
            IMM32.ImmAssociateContextEx(hwnd, null, 0);
        } catch (Throwable ignored) {}
    }

    /** 启用输入法：恢复当前前台窗口的默认 IME 关联，并打开输入法 */
    public static void enableIME() {
        if (!isSupported()) return;
        try {
            WinDef.HWND hwnd = USER32.GetForegroundWindow();
            if (hwnd == null) return;
            IMM32.ImmAssociateContextEx(hwnd, null, IACE_DEFAULT);
            // 尝试获取上下文并打开输入法状态
            Pointer himc = IMM32.ImmGetContext(hwnd);
            if (himc != null) {
                IMM32.ImmSetOpenStatus(himc, true);
                IMM32.ImmReleaseContext(hwnd, himc);
            }
        } catch (Throwable ignored) {}
    }
}
