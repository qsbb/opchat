package com.opchat.ime;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.win32.StdCallLibrary;

/**
 * 输入法冲突修复：参考 IMBlocker 的保存/恢复机制。
 *
 * 核心机制（保存并恢复原始 IME 上下文）：
 *  - 游戏内（非文本输入屏幕）：先通过 ImmGetContext 保存窗口当前的 IME 上下文（HIMC），
 *    再调用 ImmAssociateContextEx(hwnd, null, 0) 解除窗口与 IME 的关联。
 *    这样按键不再被输入法拦截，且 HIMC 中保存了用户的输入法设置（如搜狗/QQ 输入法）。
 *  - 进入聊天框 / 命令输入框等文本输入屏幕：调用 ImmAssociateContextEx(hwnd, savedIMC, 0)
 *    将保存的 IME 上下文重新关联到窗口，用户的输入法设置完整恢复。
 *
 * 相比 ImmSetOpenStatus 的方式，保存/恢复 HIMC 不会丢失用户安装的第三方输入法，
 * 因为 IME 上下文中包含了输入法选择、词库等状态。
 *
 * 仅在 Windows 平台生效，其他平台静默跳过。
 */
public final class IMEBlocker {
    private static final boolean WINDOWS;
    private static final Imm32 IMM32;
    private static final User32 USER32;

    /** 保存的窗口原始 IME 上下文，用于恢复用户的输入法设置 */
    private static Pointer savedIMC = null;

    /** IACE_DEFAULT：恢复窗口与默认 IME 的关联 */
    private static final int IACE_DEFAULT = 0xFFFFFFFF;
    /** IACE_IGNORENOCONTEXT：忽略无上下文的情况 */
    private static final int IACE_IGNORENOCONTEXT = 0x80000000;

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

        Pointer ImmCreateContext();

        boolean ImmDestroyContext(Pointer himc);
    }

    private IMEBlocker() {}

    /** 当前平台是否支持 IME 控制 */
    public static boolean isSupported() {
        return WINDOWS && IMM32 != null && USER32 != null;
    }

    /**
     * 禁用输入法：保存窗口当前的 IME 上下文，然后解除窗口与 IME 的关联。
     * 按键不再被输入法拦截，且用户的输入法设置被安全保存。
     */
    public static void disableIME() {
        if (!isSupported()) return;
        try {
            WinDef.HWND hwnd = USER32.GetForegroundWindow();
            if (hwnd == null) return;
            Pointer himc = IMM32.ImmGetContext(hwnd);
            if (himc != null) {
                // 保存原始 IME 上下文（包含用户的输入法设置）
                if (savedIMC == null) {
                    savedIMC = himc;
                }
                IMM32.ImmReleaseContext(hwnd, himc);
            }
            // 解除窗口与 IME 的关联，使用空上下文
            IMM32.ImmAssociateContextEx(hwnd, null, 0);
        } catch (Throwable ignored) {}
    }

    /**
     * 启用输入法：将保存的 IME 上下文重新关联到窗口，完整恢复用户的输入法设置。
     * 包括用户安装的第三方输入法（搜狗、QQ 等）、词库、热键等。
     */
    public static void enableIME() {
        if (!isSupported()) return;
        try {
            WinDef.HWND hwnd = USER32.GetForegroundWindow();
            if (hwnd == null) return;
            if (savedIMC != null) {
                // 恢复保存的 IME 上下文，保留用户的输入法设置
                IMM32.ImmAssociateContextEx(hwnd, savedIMC, 0);
            } else {
                // 没有保存的上下文，使用系统默认关联
                IMM32.ImmAssociateContextEx(hwnd, null, IACE_DEFAULT);
            }
        } catch (Throwable ignored) {}
    }

    /** 重置保存的 IME 上下文（游戏退出时调用） */
    public static void resetSavedIMC() {
        savedIMC = null;
    }
}
