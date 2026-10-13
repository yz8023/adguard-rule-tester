package com.yz8023.adguardtester.model;

/** 探测结论。 */
public enum ProbeStatus {
    /** 连接成功到达目标（任意 HTTP 响应）——未被拦截，需要补规则。 */
    NOT_BLOCKED("未拦截"),
    /** 系统 DNS 无法解析但公共 DoH 可解析——疑似 DNS 层拦截。 */
    BLOCKED_DNS("已拦截·DNS"),
    /** DNS 正常但连接/SSL 失败，且公共 DoH 确认域名存活——疑似连接层拦截。 */
    BLOCKED_NETWORK("已拦截·连接"),
    /** 公共 DoH 也查无此域名——域名本身已失效。 */
    DEAD("域名失效"),
    /** 无法完成验证（如 DoH 不可达），不写入规则。 */
    UNKNOWN("无法验证");

    public final String label;

    ProbeStatus(String label) {
        this.label = label;
    }
}
