package com.yz8023.adguardtester.util;

import java.util.List;

/** 文本小工具（纯 JVM）。 */
public final class Texts {

    private Texts() {
    }

    /** 取异常的简短描述（消息截断，空消息回退类名），供界面提示。 */
    public static String shortMsg(Throwable e, int maxChars) {
        String m = e.getMessage();
        if (m == null || m.isEmpty()) {
            return e.getClass().getSimpleName();
        }
        m = m.replace('\n', ' ');
        return m.length() > maxChars ? m.substring(0, maxChars) : m;
    }

    /** 以分隔符连接字符串列表。 */
    public static String join(List<String> parts, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) {
                sb.append(sep);
            }
            sb.append(p);
        }
        return sb.toString();
    }
}
