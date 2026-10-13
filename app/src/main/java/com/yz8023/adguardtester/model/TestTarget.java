package com.yz8023.adguardtester.model;

/**
 * 一条待探测的测试目标（广告/跟踪域名）。
 * 纯 JVM 类，不含 android.*，可直接参与单元测试。
 */
public final class TestTarget {
    /** 来源名称，如 paileactivist / mobileproxy / 自定义 URL。 */
    public final String sourceName;
    /** 分类（中文规范名，如 广告 / 分析 / OEM跟踪器）。 */
    public final String category;
    /** 厂商/条目名，可为空串。 */
    public final String vendor;
    /** 小写主机名。 */
    public final String host;
    /** 实际探测用 URL。 */
    public final String url;

    public TestTarget(String sourceName, String category, String vendor, String host, String url) {
        this.sourceName = sourceName == null ? "" : sourceName;
        this.category = category == null ? "未分类" : category;
        this.vendor = vendor == null ? "" : vendor;
        this.host = host == null ? "" : host;
        this.url = url == null ? "" : url;
    }

    public boolean isOem() {
        return "OEM跟踪器".equals(category);
    }

    @Override
    public String toString() {
        return host + " [" + category + "]";
    }
}
