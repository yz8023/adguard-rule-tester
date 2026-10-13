package com.yz8023.adguardtester.model;

/** 单个目标的探测结果。 */
public final class ProbeResult {
    public final TestTarget target;
    public final ProbeStatus status;
    /** 人类可读的细节，如 "HTTP 200"、"连接被重置"、"DoH: NOERROR"。 */
    public final String detail;
    public final long elapsedMs;

    public ProbeResult(TestTarget target, ProbeStatus status, String detail, long elapsedMs) {
        this.target = target;
        this.status = status;
        this.detail = detail == null ? "" : detail;
        this.elapsedMs = elapsedMs;
    }
}
