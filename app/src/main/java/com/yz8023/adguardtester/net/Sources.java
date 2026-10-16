package com.yz8023.adguardtester.net;

/**
 * 内置测试站点清单与来源选择（纯数据，纯 JVM）。
 */
public final class Sources {

    public static final String SRC1_NAME = "paileactivist/toolz";
    public static final String SRC1_URL = "https://paileactivist.github.io/toolz/adblock.html";
    public static final String SRC2_NAME = "mobileproxy.space";
    public static final String SRC2_URL = "https://mobileproxy.space/ch/adblock-test.html";
    public static final String SRC3_NAME = "Adblock Turtlecute";
    public static final String SRC3_URL = "https://adblock.turtlecute.org/";
    public static final String SRC4_NAME = "Super Adblock Test";
    public static final String SRC4_URL = "https://superadblocktest.com/";
    public static final String SRC5_NAME = "GetBlockify";
    public static final String SRC5_URL = "https://getblockify.com/adblock-test";

    public static final String[] SRC1_SCRIPTS = {"/resources/adblock/data.js"};
    public static final String[] SRC3_SCRIPTS = {"/js/index.js"};
    public static final String[] SRC4_SCRIPTS = {"/adblock.js"};
    public static final String[] NO_SCRIPTS = {};

    private Sources() {
    }

    /** 一次解析请求里的单个来源。 */
    public static final class Choice {
        public final String name;
        public final String url;
        public final String[] scriptUrlFilters;

        public Choice(String name, String url, String[] scriptUrlFilters) {
            this.name = name;
            this.url = url;
            this.scriptUrlFilters = scriptUrlFilters;
        }
    }
}
