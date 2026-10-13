(() => {
  const list = (value) => value.trim().split(/\s+/).filter(Boolean);

  const icons = {
    blocked:
      '<svg width="40" height="40" viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg"><circle cx="20" cy="20" r="18" fill="#33B175" stroke="white" stroke-width="4"/><path d="M12 20L18 26L28 16" stroke="white" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/></svg>',
    accessible:
      '<svg width="40" height="40" viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg"><circle cx="20" cy="20" r="18" fill="#CC2020" stroke="white" stroke-width="4"/><path d="M26 26L14 14M14 26L26 14" stroke="white" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/></svg>',
    collapse:
      '<svg width="22" height="22" viewBox="0 0 28 28" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M4 19L14 9L24 19" stroke="#F9F8F8" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/></svg>',
    expand:
      '<svg width="22" height="22" viewBox="0 0 28 28" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M4 9L14 19L24 9" stroke="#F9F8F8" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  };

  // Curated benchmark probes for representative ad/tracker infrastructure.
  // This is intentionally not a full filter-list import; entries are reviewed for recognizability,
  // current relevance, and low breakage risk using public adblock test sites plus
  // EasyList/EasyPrivacy/AdGuard/uBlock-style filter-list concepts.
  const HOST_TESTS = {
    Ads: {
      'Amazon Ads': list(
        `adtago.s3.amazonaws.com analyticsengine.s3.amazonaws.com advice-ads.s3.amazonaws.com advertising-api-eu.amazon.com amazon-adsystem.com aax.amazon-adsystem.com aan.amazon.com c.amazon-adsystem.com fls-na.amazon.com ir-na.amazon-adsystem.com rcm-na.amazon-adsystem.com ws-na.amazon-adsystem.com`,
      ),
      'Google Ads': list(
        `pagead2.googlesyndication.com adservice.google.com googleadservices.com pagead2.googleadservices.com afs.googlesyndication.com tpc.googlesyndication.com googletagservices.com googleads.g.doubleclick.net googleads4.g.doubleclick.net`,
      ),
      'DoubleClick / Google Ad Manager': list(
        `ad.doubleclick.net adclick.g.doubleclick.net cm.g.doubleclick.net stats.g.doubleclick.net static.doubleclick.net m.doubleclick.net mediavisor.doubleclick.net pubads.g.doubleclick.net securepubads.g.doubleclick.net pagead.l.doubleclick.net`,
      ),
      'Media.net': list(
        `media.net static.media.net contextual.media.net adservetx.media.net`,
      ),
      AdColony: list(
        `ads30.adcolony.com adc3-launch.adcolony.com events3alt.adcolony.com wd.adcolony.com`,
      ),
      Criteo: list(
        `criteo.com static.criteo.net bidder.criteo.com dis.criteo.com gum.criteo.com sslwidget.criteo.com`,
      ),
      Taboola: list(
        `cdn.taboola.com trc.taboola.com images.taboola.com nr.taboola.com api.taboola.com`,
      ),
      Outbrain: list(
        `log.outbrain.com widgets.outbrain.com odb.outbrain.com`,
      ),
      MGID: list(`mgid.com cdn.mgid.com servicer.mgid.com`),
      'Bing Ads / Microsoft Ads': list(
        `bat.bing.com bingads.microsoft.com ads.microsoft.com c.bing.com`,
      ),
      'Propeller Ads': list(
        `propellerads.com propellerclick.com onclickads.net`,
      ),
      AppLovin: list(
        `applovin.com d.applovin.com rt.applovin.com ms.applovin.com`,
      ),
      'Vungle / Liftoff': list(`api.vungle.com vungle.com liftoff.io`),
      'Xandr / AppNexus': list(
        `adnxs.com ib.adnxs.com secure.adnxs.com acdn.adnxs.com prebid.adnxs.com sin3-ib.adnxs.com`,
      ),
      PubMatic: list(
        `pubmatic.com ads.pubmatic.com image6.pubmatic.com hbopenbid.pubmatic.com simage2.pubmatic.com`,
      ),
      OpenX: list(`openx.net us-ads.openx.net rtb.openx.net u.openx.net oas.openx.net`),
      'Magnite / Rubicon / SpotX': list(
        `rubiconproject.com pixel.rubiconproject.com fastlane.rubiconproject.com optimized-by.rubiconproject.com spotxchange.com search.spotxchange.com`,
      ),
      'Index Exchange / Casale Media': list(
        `indexexchange.com casalemedia.com as.casalemedia.com cdn.indexexchange.com htlbid.com htlb.casalemedia.com`,
      ),
      'Yahoo / Oath': list(
        `ads.yahoo.com analytics.yahoo.com geo.yahoo.com udc.yahoo.com udcm.yahoo.com advertising.yahoo.com analytics.query.yahoo.com partnerads.ysm.yahoo.com log.fc.yahoo.com gemini.yahoo.com adtech.yahooinc.com`,
      ),
      'Unity Ads': list(
        `auction.unityads.unity3d.com webview.unityads.unity3d.com config.unityads.unity3d.com adserver.unityads.unity3d.com unityads.unity3d.com`,
      ),
      Yandex: list(
        `metrika.yandex.ru adfox.yandex.ru adfstat.yandex.ru appmetrica.yandex.ru extmaps-api.yandex.net offerwall.yandex.net mc.yandex.ru advertising.yandex.ru`,
      ),
      'Mobile Ad Networks': list(
        `live.chartboost.com init.supersonicads.com api.fyber.com inmobi.com ironsource.mobi outcome-ssp.supersonicads.com`,
      ),
      'YouTube / Video Ads': list(
        `s.youtube.com redirector.googlevideo.com ads.youtube.com`,
      ),
      'Ad Exchanges & SSPs': list(
        `adsrvr.org insights.adsrvr.org match.adsrvr.org smartyads.com ad.gt contextweb.com sharethrough.com stackadapt.com stickyadstv.com ads.stickyadstv.com eb2.3lift.com tlx.3lift.com apex.go.sonobi.com c.gumgum.com a.teads.tv cdn.teads.tv cdn.kargo.com sync.kargo.com`,
      ),
      'AdRoll / NextRoll': list(`d.adroll.com s.adroll.com adroll.com`),
      'Pangle / ByteDance': list(`pangleglobal.com`),
      'Ad Verification': list(
        `doubleverify.com cdn.doubleverify.com tps.doubleverify.com pixel.adsafeprotected.com static.adsafeprotected.com fw.adsafeprotected.com insightexpressai.com`,
      ),
    },

    Analytics: {
      'Google Analytics': list(
        `analytics.google.com region1.google-analytics.com click.googleanalytics.com google-analytics.com ssl.google-analytics.com www.google-analytics.com www.google-analytics.com/g/collect`,
      ),
      'Google Tag Manager': list(
        `googletagmanager.com tagmanager.google.com www.googletagmanager.com`,
      ),
      'Adobe Analytics': list(`analytics.adobe.io omtrdc.net metrics.adobe.com`),
      'Microsoft Clarity': list(`clarity.ms c.clarity.ms s.clarity.ms`),
      Hotjar: list(
        `adm.hotjar.com identify.hotjar.com insights.hotjar.com script.hotjar.com surveys.hotjar.com careers.hotjar.com events.hotjar.io static.hotjar.com vars.hotjar.com in.hotjar.com`,
      ),
      MouseFlow: list(
        `cdn.mouseflow.com o2.mouseflow.com gtm.mouseflow.com api.mouseflow.com tools.mouseflow.com cdn-test.mouseflow.com`,
      ),
      FreshWorks: list(
        `claritybt.freshmarketer.com fwtracks.freshmarketer.com`,
      ),
      LuckyOrange: list(
        `api.luckyorange.com realtime.luckyorange.com cdn.luckyorange.com w1.luckyorange.com upload.luckyorange.net cs.luckyorange.net settings.luckyorange.net`,
      ),
      'Stats WP': list(`stats.wp.com`),
      'Heap Analytics': list(`cdn.heapanalytics.com`),
      Mixpanel: list(
        `api.mixpanel.com api-js.mixpanel.com decide.mixpanel.com`,
      ),
      Amplitude: list(`api.amplitude.com api2.amplitude.com`),
      Segment: list(`cdn.segment.com api.segment.io events.segment.io`),
      FullStory: list(`rs.fullstory.com edge.fullstory.com`),
      Quantcast: list(`quantserve.com pixel.quantserve.com quantcast.com`),
      'comScore / Scorecard': list(
        `sb.scorecardresearch.com b.scorecardresearch.com`,
      ),
      'Modern Product Analytics': list(
        `static.cloudflareinsights.com cloudflareinsights.com app.posthog.com eu.posthog.com us.i.posthog.com rudderstack.com cdn.rudderlabs.com snowplowanalytics.com tracker.snowplowanalytics.com`,
      ),
    },

    'Tracking & Fingerprinting': {
      'Browser Fingerprinting': list(`fingerprintjs.com fpjs.io`),
      'Behavioral Analytics': list(
        `siftscience.com cdn.siftscience.com cdn.permutive.com`,
      ),
      'Identity Resolution': list(
        `bluekai.com tags.bluekai.com onetag-sys.com pippio.com id5-sync.com sync.mathtag.com pixel.mathtag.com crwdcntrl.net bcp.crwdcntrl.net bidswitch.net sync-tm.everesttech.net prod.uidapi.com`,
      ),
      LiveRamp: list(`idsync.rlcdn.com api.rlcdn.com`),
      Lotame: list(`tags.crwdcntrl.net ad.crwdcntrl.net sync.crwdcntrl.net`),
      'Mobile Attribution': list(
        `app.appsflyer.com appsflyer.com app.adjust.com adjust.com api2.branch.io bnc.lt kochava.com control.kochava.com sdk-api-v1.singular.net`,
      ),
      'CleverTap / Push': list(`wzrkt.com`),
    },

    'Error Trackers': {
      Bugsnag: list(
        `notify.bugsnag.com sessions.bugsnag.com api.bugsnag.com app.bugsnag.com`,
      ),
      Sentry: list(
        `browser.sentry-cdn.com app.getsentry.com ingest.sentry.io o0.ingest.sentry.io`,
      ),
      'New Relic': list(`bam.nr-data.net bam-cell.nr-data.net js-agent.newrelic.com`),
      Datadog: list(`browser-intake-datadoghq.com rum.browser-intake-datadoghq.com`),
      LogRocket: list(`cdn.lr-ingest.com r.lr-ingest.com`),
    },

    'Advanced / Risky Host Checks': {
      'Browser Cryptominers': list(
        `coinimp.com www.coinimp.com webminepool.com minero.cc mineralt.io monerominer.rocks`,
      ),
      'Malvertising / Popups': list(
        `popads.net popcash.net popmyads.com clickadu.com trafficjunky.net exoclick.com juicyads.com`,
      ),
      'Suspicious Script Hosts': list(`statdynamic.com`),
    },

    'Social Trackers': {
      'Facebook / Meta': list(
        `pixel.facebook.com an.facebook.com connect.facebook.net graph.facebook.com tr.facebook.com`,
      ),
      Instagram: list(`graph.instagram.com i.instagram.com`),
      Snapchat: list(
        `sc-static.net tr.snapchat.com ads.snapchat.com sc-analytics.appspot.com`,
      ),
      LinkedIn: list(
        `ads.linkedin.com analytics.pointdrive.linkedin.com snap.licdn.com px.ads.linkedin.com dc.ads.linkedin.com`,
      ),
      'X / Twitter': list(
        `static.ads-twitter.com ads-api.twitter.com ads-api.x.com analytics.twitter.com analytics.x.com ads.x.com`,
      ),
      Reddit: list(`events.reddit.com events.redditmedia.com pixel.redditmedia.com d.reddit.com`),
      TikTok: list(
        `ads-api.tiktok.com analytics.tiktok.com ads-sg.tiktok.com analytics-sg.tiktok.com business-api.tiktok.com ads.tiktok.com log.byteoversea.com mon.byteoversea.com mcs-va.tiktokv.com mon.tiktokv.com`,
      ),
      Pinterest: list(
        `ads.pinterest.com ct.pinterest.com log.pinterest.com analytics.pinterest.com trk.pinterest.com widgets.pinterest.com`,
      ),
      Quora: list(`pixel.quora.com qevents.quora.com`),
      Tumblr: list(`px.srvcs.tumblr.com`),
      'VK / VKontakte': list(`ads.vk.com vk.com/rtrg`),
      'Mail.ru': list(`ad.mail.ru top-fwz1.mail.ru`),
    },

    'OEM Vendors': {
      Apple: list(
        `advertising.apple.com tr.iadsdk.apple.com iadsdk.apple.com metrics.icloud.com metrics.apple.com metrics.mzstatic.com api-adservices.apple.com books-analytics-events.apple.com notes-analytics-events.apple.com xp.apple.com`,
      ),
      Realme: list(
        `iot-eu-logser.realme.com iot-logser.realme.com bdapi-ads.realmemobile.com bdapi-in-ads.realmemobile.com`,
      ),
      Oppo: list(
        `adsfs.oppomobile.com adx.ads.oppomobile.com ck.ads.oppomobile.com data.ads.oppomobile.com`,
      ),
      OnePlus: list(`click.oneplus.cn open.oneplus.net`),
      Huawei: list(
        `metrics.data.hicloud.com metrics2.data.hicloud.com grs.hicloud.com logservice.hicloud.com logservice1.hicloud.com logbak.hicloud.com ads.huawei.com`,
      ),
      Xiaomi: list(
        `api.ad.xiaomi.com data.mistat.xiaomi.com data.mistat.india.xiaomi.com data.mistat.rus.xiaomi.com sdkconfig.ad.xiaomi.com sdkconfig.ad.intl.xiaomi.com globalapi.ad.xiaomi.com tracking.rus.miui.com tracking.miui.com`,
      ),
      Samsung: list(
        `samsungads.com smetrics.samsung.com nmetrics.samsung.com samsung-com.112.2o7.net analytics-api.samsunghealthcn.com config.samsungads.com`,
      ),
      Vivo: list(`adlog.vivo.com ads-api.vivo.com`),
      Lenovo: list(`a.lenovo.com`),
      LG: list(
        `us.info.lgsmartad.com us.ibs.lgappstv.com ad.lgappstv.com info.lgsmartad.com ngfts.lge.com yumenetworks.com smartclip.net smartclip.com`,
      ),
      'Microsoft / Windows': list(
        `settings-win.data.microsoft.com vortex.data.microsoft.com vortex-win.data.microsoft.com watson.telemetry.microsoft.com telemetry.microsoft.com browser.events.data.msn.com`,
      ),
      'FireTV / Amazon': list(
        `device-metrics-us.amazon.com device-metrics-us-2.amazon.com mads-eu.amazon.com`,
      ),
      'Smart TV / Roku / Vizio': list(
        `logs.roku.com ads.roku.com amoeba.web.roku.com ads.vizio.com tvinteractive.tv tvpixel.com`,
      ),
      'Google / Pixel / Android': list(
        `firebase-settings.crashlytics.com`,
      ),
    },

    'Consent Management': {
      'CMP Vendors': list(
        `cdn.cookielaw.org geolocation.onetrust.com privacyportal.onetrust.com consent.cookiebot.com consentcdn.cookiebot.com cookiebot.com consent.trustarc.com sdk.privacy-center.org cdn.privacy-mgmt.com app.usercentrics.eu cmp.usercentrics.eu cmp.inmobi.com sourcepoint.mgr.consensu.org cmp.osano.com fundingchoicesmessages.google.com`,
      ),
    },

    'Affiliate Networks': {
      'Commission Junction / CJ': list(
        `www.anrdoezrs.net www.dpbolvw.net www.tkqlhce.com`,
      ),
      ShareASale: list(`shareasale.com`),
      'Rakuten Advertising': list(
        `click.linksynergy.com ad.linksynergy.com track.linksynergy.com`,
      ),
      Impact: list(`d.impactradius-event.com`),
      Awin: list(`www.awin1.com zenaps.com prf.hn`),
      PartnerStack: list(`partnerstack.com api.partnerstack.com`),
      Refersion: list(`refersion.com`),
      Pepperjam: list(`t.pepperjamnetwork.com`),
      Skimlinks: list(
        `s.skimresources.com t.skimresources.com go.skimresources.com redirector.skimresources.com go.redirectingat.com`,
      ),
      'Viglink / Sovrn': list(
        `redirect.viglink.com cdn.viglink.com api.viglink.com`,
      ),
    },

    'A/B Testing': {
      'Experiment Platforms': list(
        `cdn.optimizely.com logx.optimizely.com api.optimizely.com cdn.dynamicyield.com st.dynamicyield.com events.launchdarkly.com clientstream.launchdarkly.com`,
      ),
    },

    'Email Tracking & Live Chat': {
      'Email Marketing': list(
        `list-manage.com track.hubspot.com munchkin.marketo.net click.mailchimp.com`,
      ),
      'Live Chat Trackers': list(`widget.intercom.io js.driftt.com`),
      'Lifecycle Messaging': list(
        `sdk.iad-01.braze.com cdn.onesignal.com api.onesignal.com static.klaviyo.com a.klaviyo.com customer.io track.customer.io track.customer.io/events`,
      ),
    },

    'Video Ads': {
      'VAST / VPAID': list(
        `dai.google.com pubads.g.doubleclick.net/gampad/ads securepubads.g.doubleclick.net/gampad/ads googleads.g.doubleclick.net/pagead/ads`,
      ),
      'JW Player Ads': list(`g.jwpsrv.com ssl.p.jwpcdn.com prd.jwpltx.com`),
      'FreeWheel / Comcast': list(`mssl.fwmrm.net bea4.v.fwmrm.net 2975c.v.fwmrm.net`),
      Connatix: list(`cd.connatix.com capi.connatix.com vid.connatix.com`),
      Brightcove: list(`metrics.brightcove.com`),
      Innovid: list(`s.innovid.com`),
      'Video Ad Servers': list(`tremorhub.com ads.tremorhub.com`),
    },
  };

  const TEST_TIMEOUT_MS = 5000;
  const MAX_CONCURRENT_TESTS = 12;
  const HOST_RESOURCE_PATHS = ['/', '/favicon.ico', '/robots.txt'];
  // Small curated ad-like URL path set for resource/path probes. These patterns are
  // inspired by common EasyList/EasyPrivacy/AdGuard/uBlock-style URL rules and public
  // adblock test pages; it is intentionally short to keep the benchmark fast.
  const AD_PATTERN_PATHS = [
    '/ads',
    '/ad',
    '/adserver',
    '/adserver/ads',
    '/pagead',
    '/pagead/ads',
    '/gampad/ads',
    '/doubleclick/ad',
    '/vast',
    '/vpaid',
    '/prebid',
    '/bidder',
    '/hb',
    '/ads.js',
    '/ad.js',
    '/pagead.js',
    '/advertisement.js',
    '/adsbygoogle.js',
    '/analytics/collect',
    '/tracking/pixel',
  ];
  const DOMAIN_LIST_REVIEWED_AT = '2026-08-03';
  const DOMAIN_LIST_NOTE =
    'This is a curated live-probe benchmark reviewed on 2026-08-03 using public adblock test sites and EasyList, EasyPrivacy, AdGuard, uBlock, Peter Lowe, StevenBlack, OISD, and Hagezi-style coverage as references. It is not a full filter-list mirror, so it favors representative high-signal ad, tracker, telemetry, consent, affiliate, video ad, and OEM endpoints over thousands of site-specific rules.';

  const state = {
    total: 0,
    checked: 0,
    blocked: 0,
    accessible: 0,
    isRunning: false,
  };
  const categoryStats = {};
  const testRows = [];
  const rowDiagnostics = new WeakMap();
  const rowTestConfigs = new WeakMap();

  const SCORE_MODULES = {
    'Ad Network Blocking': ['Ads'],
    'Tracker Blocking': [
      'Analytics',
      'Tracking & Fingerprinting',
      'Social Trackers',
      'Email Tracking & Live Chat',
    ],
    'Video Ad Blocking': ['Video Ads'],
    'Affiliate / Redirect Blocking': ['Affiliate Networks'],
    'Telemetry / OEM Blocking': ['OEM Vendors'],
    'Consent / Experiment Blocking': ['Consent Management', 'A/B Testing'],
    'Advanced Risky Host Checks': ['Advanced / Risky Host Checks'],
  };

  const MODULE_WEIGHTS = {
    'Ad Network Blocking': 25,
    'Tracker Blocking': 25,
    'Video Ad Blocking': 10,
    'Affiliate / Redirect Blocking': 5,
    'Telemetry / OEM Blocking': 5,
    'Consent / Experiment Blocking': 5,
    'Advanced Risky Host Checks': 5,
  };

  const escapeHtml = (text) =>
    String(text)
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');

  const unique = (items) => [
    ...new Set(items.map((item) => String(item).trim()).filter(Boolean)),
  ];

  function countHostTests() {
    return Object.values(HOST_TESTS).reduce((sum, category) => {
      return (
        sum +
        Object.values(category).reduce(
          (inner, hosts) => inner + unique(hosts).length,
          0,
        )
      );
    }, 0);
  }

  function getUniqueHostCount() {
    return new Set(
      Object.values(HOST_TESTS).flatMap((category) =>
        Object.values(category).flatMap((hosts) => unique(hosts)),
      ),
    ).size;
  }

  const getPercent = (blocked, total) =>
    total ? Math.round((blocked / total) * 100) : 0;

  const getRawOverallScore = () => getPercent(state.blocked, state.total);

  const getPercentage = () => getWeightedOverallScore();

  function resetCategoryStats() {
    Object.keys(categoryStats).forEach((category) => {
      delete categoryStats[category];
    });
  }

  function ensureCategoryStats(category) {
    if (!categoryStats[category]) {
      categoryStats[category] = {
        total: 0,
        checked: 0,
        blocked: 0,
        accessible: 0,
      };
    }

    return categoryStats[category];
  }

  function registerCategoryTest(category) {
    ensureCategoryStats(category).total += 1;
  }

  function addCategoryResult(category, blocked) {
    const stats = ensureCategoryStats(category);
    stats.checked += 1;

    if (blocked) stats.blocked += 1;
    else stats.accessible += 1;
  }

  function adjustCategoryResult(category, previousStatus, nextStatus) {
    const stats = ensureCategoryStats(category);

    if (previousStatus === 'accessible') stats.accessible -= 1;
    if (previousStatus === 'blocked') stats.blocked -= 1;
    if (nextStatus === 'accessible') stats.accessible += 1;
    if (nextStatus === 'blocked') stats.blocked += 1;
  }

  function getModuleStats(moduleName) {
    const categories = SCORE_MODULES[moduleName] || [];

    return categories.reduce(
      (moduleStats, category) => {
        const stats = categoryStats[category];
        if (!stats || !stats.total) return moduleStats;

        moduleStats.total += stats.total;
        moduleStats.checked += stats.checked;
        moduleStats.blocked += stats.blocked;
        moduleStats.accessible += stats.accessible;

        return moduleStats;
      },
      { total: 0, checked: 0, blocked: 0, accessible: 0 },
    );
  }

  function getModuleScores() {
    return Object.keys(SCORE_MODULES)
      .map((moduleName) => {
        const stats = getModuleStats(moduleName);

        return {
          name: moduleName,
          ...stats,
          score: getPercent(stats.blocked, stats.total),
          weight: MODULE_WEIGHTS[moduleName] || 0,
        };
      })
      .filter((moduleScore) => moduleScore.total > 0);
  }

  function getWeightedOverallScore() {
    const modules = getModuleScores();
    const activeWeight = modules.reduce(
      (sum, moduleScore) => sum + moduleScore.weight,
      0,
    );

    if (!activeWeight) return getRawOverallScore();

    return Math.round(
      modules.reduce(
        (sum, moduleScore) => sum + moduleScore.score * moduleScore.weight,
        0,
      ) / activeWeight,
    );
  }

  function getCategoryBreakdown() {
    return Object.entries(categoryStats)
      .map(([category, stats]) => ({
        category,
        ...stats,
        score: getPercent(stats.blocked, stats.total),
      }))
      .filter((stats) => stats.total > 0)
      .sort((a, b) => a.score - b.score || a.category.localeCompare(b.category));
  }

  function getTopMissedCategories() {
    return getCategoryBreakdown().slice(0, 3);
  }

  function getStrengthLabel(score) {
    if (score >= 85) return 'Strong';
    if (score >= 70) return 'Good';
    if (score >= 40) return 'Weak';
    return 'Poor';
  }

  function getStrongestModule() {
    return getModuleScores()
      .slice()
      .sort((a, b) => b.score - a.score || a.name.localeCompare(b.name))[0] || null;
  }

  function getWeakestModule() {
    return getModuleScores()
      .slice()
      .sort((a, b) => a.score - b.score || a.name.localeCompare(b.name))[0] || null;
  }

  function getBaselineStatusForReport() {
    return null;
  }

  function getResultConfidence() {
    if (state.checked < state.total) return 'Low';
    if (getWeightedOverallScore() >= 85) return 'High';
    return 'Medium';
  }

  function setPageState(status) {
    document.body.classList.remove('test-idle', 'test-running', 'test-complete');
    document.body.classList.add(`test-${status}`);
  }

  function setButtonState(label, options = {}) {
    const button = document.getElementById('retest');
    if (!button) return;

    const labelEl = button.querySelector('.actionLabel');
    const iconEl = button.querySelector('.actionIcon i');

    if (labelEl) labelEl.textContent = label;
    if (iconEl && options.icon) iconEl.className = options.icon;

    button.disabled = Boolean(options.disabled);
    button.classList.toggle('disabled', Boolean(options.disabled));
    button.setAttribute('aria-label', options.ariaLabel || label);
  }

  function updateProgressUi(final = false) {
    const progress = document.getElementById('testProgress');
    const scoreState = document.getElementById('scoreState');
    const meterFill = document.getElementById('scoreMeterFill');
    const score = getPercentage();

    if (progress) {
      progress.textContent = final
        ? `Completed: ${state.blocked} blocked and ${state.accessible} accessible from ${state.total} checks.`
        : state.total
          ? `Testing in progress: ${state.checked} of ${state.total} checks complete.`
          : "Tap Start test to check this browser's ad blocking.";
    }

    if (scoreState) {
      scoreState.textContent = final ? 'Complete' : state.isRunning ? 'Testing' : 'Ready';
      scoreState.style.color = final ? 'var(--greenColor)' : '';
    }

    if (meterFill) {
      meterFill.style.width = `${score}%`;
    }
  }

  function formatReportDate(date = new Date()) {
    const pad = (value) => String(value).padStart(2, '0');

    return (
      `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ` +
      `${pad(date.getHours())}:${pad(date.getMinutes())}`
    );
  }

  function formatPassportDate(date = new Date()) {
    return date.toLocaleString(undefined, {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
    });
  }

  function timeoutSignal(ms) {
    const controller = new AbortController();
    let timedOut = false;
    const id = setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, ms);

    return {
      signal: controller.signal,
      clear: () => clearTimeout(id),
      get timedOut() {
        return timedOut;
      },
    };
  }

  function parseTarget(target) {
    const clean = String(target || '')
      .trim()
      .replace(/^https?:\/\//i, '')
      .replace(/^\/+/g, '');
    const index = clean.indexOf('/');
    return index === -1
      ? { host: clean.toLowerCase(), path: '' }
      : { host: clean.slice(0, index).toLowerCase(), path: clean.slice(index) };
  }

  function buildHostUrl(target, fallbackPath) {
    const { host, path } = parseTarget(target);
    const targetPath = path || fallbackPath || '/';
    const separator = targetPath.includes('?') ? '&' : '?';
    return `https://${host}${targetPath}${separator}adblock_test=${Date.now()}_${Math.random().toString(36).slice(2)}`;
  }

  function buildSameOriginUrl(target) {
    const url = new URL(String(target || '/'), window.location.href);
    url.searchParams.set(
      'adblock_test',
      `${Date.now()}_${Math.random().toString(36).slice(2)}`,
    );
    return url.toString();
  }

  function isBlockerLikeTarget(target) {
    return /ad|ads|advert|analytics|track|pixel|beacon|banner|sponsor|doubleclick|googlesyndication|tagmanager/i.test(
      String(target || ''),
    );
  }

  function isoNow() {
    return new Date().toISOString();
  }

  function durationFrom(startMs) {
    return Math.round(performance.now() - startMs);
  }

  async function attemptFetchDiagnostic(url, path, mode = 'no-cors') {
    const timeout = timeoutSignal(TEST_TIMEOUT_MS);
    const startedAt = isoNow();
    const startMs = performance.now();
    const attempt = {
      url,
      path,
      method: 'GET',
      mode,
      startedAt,
      endedAt: null,
      durationMs: null,
      resolved: false,
      responseType: null,
      errorName: null,
      errorMessage: null,
      outcome: null,
    };

    try {
      const response = await fetch(url, {
        method: 'GET',
        mode,
        cache: 'no-store',
        credentials: mode === 'same-origin' ? 'same-origin' : 'omit',
        redirect: 'follow',
        signal: timeout.signal,
      });

      attempt.resolved = true;
      attempt.responseType = response.type || null;
      attempt.outcome = 'fetch-resolved';
    } catch (error) {
      attempt.errorName = error && error.name ? error.name : 'Error';
      attempt.errorMessage = error && error.message ? error.message : '';
      attempt.outcome = timeout.timedOut
        ? 'timeout'
        : attempt.errorName === 'AbortError'
          ? 'abort-error'
          : 'fetch-rejected';
    } finally {
      timeout.clear();
      attempt.endedAt = isoNow();
      attempt.durationMs = durationFrom(startMs);
    }

    return attempt;
  }

  async function runHostDiagnostic(hostOrUrl) {
    const { host, path } = parseTarget(hostOrUrl);
    const startedAt = isoNow();
    const startMs = performance.now();

    if (!host) {
      return {
        label: hostOrUrl,
        type: 'host',
        status: 'blocked',
        blocked: true,
        method: 'GET',
        fetchMode: 'no-cors',
        pathsTested: [],
        attemptedUrls: [],
        attempts: [],
        startedAt,
        endedAt: isoNow(),
        durationMs: durationFrom(startMs),
        outcome: 'fetch-rejected',
        responseType: null,
        errorName: 'InvalidHost',
        errorMessage: 'No host was available to test.',
        reason: 'The tested item did not include a valid host.',
        confidence: 'high',
      };
    }

    const paths = path ? [''] : HOST_RESOURCE_PATHS;
    const attempts = await Promise.all(
      paths.map((resourcePath) => {
        const url = buildHostUrl(hostOrUrl, resourcePath);
        return attemptFetchDiagnostic(url, path || resourcePath, 'no-cors');
      }),
    );
    const resolvedAttempt = attempts.find((attempt) => attempt.resolved);
    const blocked = !resolvedAttempt;
    const timeoutAttempt = attempts.find((attempt) => attempt.outcome === 'timeout');
    const responseType = resolvedAttempt ? resolvedAttempt.responseType : null;
    const firstError = attempts.find((attempt) => attempt.errorName);
    const outcome = resolvedAttempt
      ? 'fetch-resolved'
      : timeoutAttempt
        ? 'timeout'
        : attempts[0]?.outcome || 'fetch-rejected';
    const reason = resolvedAttempt
      ? `The request resolved for path ${resolvedAttempt.path || '/'}.`
      : 'All tested paths failed to resolve.';
    const confidence = resolvedAttempt
      ? responseType === 'opaque'
        ? 'low'
        : 'medium'
      : timeoutAttempt
        ? 'medium'
        : isBlockerLikeTarget(hostOrUrl)
          ? 'high'
          : 'medium';

    return {
      label: hostOrUrl,
      type: 'host',
      status: blocked ? 'blocked' : 'accessible',
      blocked,
      method: 'GET',
      fetchMode: 'no-cors',
      pathsTested: paths.map((resourcePath) => path || resourcePath),
      attemptedUrls: attempts.map((attempt) => attempt.url),
      attempts,
      startedAt,
      endedAt: isoNow(),
      durationMs: durationFrom(startMs),
      outcome,
      responseType,
      errorName: blocked
        ? timeoutAttempt
          ? 'RequestTimedOut'
          : 'RequestBlockedOrFailed'
        : firstError
          ? firstError.errorName
          : null,
      errorMessage: blocked
        ? timeoutAttempt
          ? 'Every tested path timed out before the request completed.'
          : 'Every tested GET no-cors request was rejected or stopped before completion. This is commonly caused by built-in content blocker rules, private browser protection, DNS filtering, or network failure.'
        : firstError
          ? firstError.errorMessage
          : null,
      reason,
      confidence,
    };
  }

  function getElementVisibilityDiagnostic(el) {
    const isInDOM = Boolean(el && document.body.contains(el));
    const style = isInDOM ? window.getComputedStyle(el) : null;
    const rect = isInDOM ? el.getBoundingClientRect() : null;
    const result = {
      display: style ? style.display : null,
      visibility: style ? style.visibility : null,
      opacity: style ? style.opacity : null,
      width: rect ? rect.width : 0,
      height: rect ? rect.height : 0,
      isInDOM,
    };

    result.hidden =
      !isInDOM ||
      (style && style.display === 'none') ||
      (style && style.visibility === 'hidden') ||
      (style && Number(style.opacity) === 0) ||
      !rect ||
      rect.width === 0 ||
      rect.height === 0;

    return result;
  }

  async function runCosmeticDiagnostic(test) {
    const startedAt = isoNow();
    const startMs = performance.now();
    const el = document.createElement('div');
    el.className = test.classes;
    el.setAttribute('aria-hidden', 'true');
    el.textContent = 'Advertisement';
    el.style.cssText =
      'width:300px;height:250px;position:absolute;left:-9999px;top:-9999px;pointer-events:none;';
    document.body.appendChild(el);

    await new Promise((resolve) =>
      requestAnimationFrame(() => setTimeout(resolve, 80)),
    );

    const visibility = getElementVisibilityDiagnostic(el);
    const blocked = visibility.hidden;
    el.remove();

    return {
      label: test.label,
      type: 'cosmetic',
      status: blocked ? 'blocked' : 'accessible',
      blocked,
      method: null,
      fetchMode: null,
      attemptedUrls: [],
      pathsTested: [],
      attempts: [],
      testedClasses: test.classes,
      visibility,
      startedAt,
      endedAt: isoNow(),
      durationMs: durationFrom(startMs),
      outcome: blocked ? 'cosmetic-hidden' : 'cosmetic-visible',
      responseType: null,
      errorName: blocked ? 'CosmeticElementHidden' : null,
      errorMessage: blocked
        ? 'The bait element was hidden, removed, transparent, or measured as zero-size.'
        : null,
      reason: blocked
        ? 'The bait element was hidden, removed, transparent, or measured as zero-size.'
        : 'The bait element remained visible and measurable.',
      confidence: blocked ? 'high' : 'medium',
    };
  }

  async function runScriptPathDiagnostic(config, fallbackLabel) {
    const target = config.path || config.url || fallbackLabel || '/';
    const url = buildSameOriginUrl(target);
    const startedAt = isoNow();
    const startMs = performance.now();
    const attempt = await attemptFetchDiagnostic(url, target, 'same-origin');
    const blocked = !attempt.resolved;

    return {
      label: fallbackLabel || target,
      type: 'script-path',
      status: blocked ? 'blocked' : 'accessible',
      blocked,
      method: 'GET',
      fetchMode: 'same-origin',
      pathsTested: [target],
      attemptedUrls: [url],
      attempts: [attempt],
      startedAt,
      endedAt: isoNow(),
      durationMs: durationFrom(startMs),
      outcome: blocked ? 'local-bait-blocked' : 'local-bait-accessible',
      responseType: attempt.responseType,
      errorName: attempt.errorName,
      errorMessage: attempt.errorMessage,
      reason: blocked
        ? 'The local bait path fetch rejected before the browser reached the server.'
        : 'The local bait path fetch resolved, so the browser reached the server. A resolving 404 is still Accessible for this test.',
      confidence: blocked ? 'high' : 'medium',
    };
  }

  function setRowDiagnostic(row, diagnostic) {
    rowDiagnostics.set(row, {
      ...diagnostic,
      label: row.dataset.label || diagnostic.label,
      category: row.dataset.category || diagnostic.category || '',
      group: row.dataset.group || diagnostic.group || '',
      type: row.dataset.type || diagnostic.type,
    });
  }

  function getRowDiagnostic(row) {
    return rowDiagnostics.get(row) || null;
  }

  function getRowTestConfig(row) {
    return rowTestConfigs.get(row) || {};
  }

  function createSection(title) {
    const wrapper = document.getElementById('test');
    if (!wrapper) return null;

    const section = document.createElement('div');
    section.className = 'grid';
    section.id = title;
    section.innerHTML =
      '<div class="categoryDiv d-md-flex"><h2 class="adCategory text-uppercase">' +
      escapeHtml(title) +
      `</h2> <button class="toggle-button"><span class="status text-start">Collapse</span> <span class="svg">${icons.collapse}</span></button></div>`;

    const rowDiv = document.createElement('div');
    rowDiv.classList.add('rowDiv');
    section.appendChild(rowDiv);
    wrapper.appendChild(section);

    const toggleButton = section.querySelector('.toggle-button');
    const svg = section.querySelector('.svg');
    const status = section.querySelector('.status');

    if (toggleButton && svg && status) {
      toggleButton.addEventListener('click', () => {
        rowDiv.classList.toggle('d-none');

        const collapsed = rowDiv.classList.contains('d-none');
        status.textContent = collapsed ? 'Expand' : 'Collapse';
        svg.innerHTML = collapsed ? icons.expand : icons.collapse;
      });
    }

    return rowDiv;
  }

  function createGroup(parent, title) {
    const group = document.createElement('div');
    group.classList.add('svgDiv', 'd-block', 'test', 'show', 'column');
    group.id = title;
    group.innerHTML = `<div class="fDiv"><h3 class="domain">${escapeHtml(title)}</h3></div>`;

    const listEl = document.createElement('div');
    group.appendChild(listEl);
    parent.appendChild(group);

    return listEl;
  }

  function createRow(label, container, meta = {}) {
    const row = document.createElement('div');
    row.classList.add('adblockHostDiv', 'is-testing');
    row.tabIndex = -1;
    row.setAttribute('aria-disabled', 'true');
    row.style.color = 'grey';
    row.dataset.label = label;
    row.dataset.category = meta.category || '';
    row.dataset.group = meta.group || '';
    row.dataset.type = meta.type || '';
    rowTestConfigs.set(row, meta.config || {});
    row.innerHTML =
      `<span class="text-start">${escapeHtml(label)}</span>` +
      '<button type="button" class="diagnosticDetailsBtn d-none" aria-label="View test diagnostic details" title="View details">View details</button>';
    container.appendChild(row);
    testRows.push(row);

    return row;
  }

  function setRowStatus(row, blocked, status = blocked ? 'blocked' : 'accessible') {
    row.dataset.status = status;

    if (state.isRunning) {
      row.classList.add('is-testing');
      row.classList.remove('blockedGreen', 'blockedRed');
      row.tabIndex = -1;
      row.setAttribute('aria-disabled', 'true');
      row.style.backgroundColor = '';
      row.style.color = '';
      return;
    }

    applyRowVisualStatus(row);
  }

  function applyRowVisualStatus(row) {
    const status = row.dataset.status;
    if (status !== 'blocked' && status !== 'accessible') return;

    row.classList.remove('is-testing', 'blockedGreen', 'blockedRed');
    row.classList.add(status === 'blocked' ? 'blockedGreen' : 'blockedRed');
    row.tabIndex = 0;
    row.setAttribute('aria-disabled', 'false');
    row.style.backgroundColor =
      status === 'blocked' ? 'var(--greenColor)' : 'var(--redColor)';
    row.style.color = '#fff';

    const detailsButton = row.querySelector('.diagnosticDetailsBtn');
    if (detailsButton) {
      detailsButton.classList.remove('d-none');
    }
  }

  function revealCompletedRows() {
    testRows.forEach(applyRowVisualStatus);
  }

  function updateSummary(final = false) {
    const percentage = document.getElementById('percentage');
    const log = document.querySelector('#adblockTestLog');

    if (percentage) {
      percentage.innerHTML = `${getPercentage()}<i class="fa-solid fa-percent ms-2 mt-5" style="color: #ffffff;"></i>`;
    }

    if (!log) return;

    const titleLabel = final ? 'Total checks' : 'Testing in progress';
    const titleValue = final ? state.total : `${state.checked}/${state.total}`;
    const trigger = final ? ' statTrigger' : '';
    const attrs = final ? ' role="button" tabindex="0"' : '';
    const info = final
      ? ' <i class="fa-solid fa-circle-info statInfoIcon"></i>'
      : '';

    log.innerHTML =
      '<div class="totalStatCard">' +
      `<span class="totalStatLabel">${titleLabel}</span>` +
      `<strong class="totalStatValue">${titleValue}</strong>` +
      '</div>' +
      '<div class="stats d-md-flex">' +
      `<h4 class="indStat${trigger}"${attrs} data-status="blocked" aria-label="Show blocked tests"> ${icons.blocked}<span class="statText"><strong class="statValue">${state.blocked}</strong><span class="statLabel">Blocked</span></span>${info}</h4>` +
      `<h4 class="indStat${trigger}"${attrs} data-status="accessible" aria-label="Show reachable tests"> ${icons.accessible}<span class="statText"><strong class="statValue">${state.accessible}</strong><span class="statLabel">Reachable</span></span>${info}</h4>` +
      '</div>';

    log.style.display = 'block';

    renderBenchmarkDashboard();
  }

  function getBenchmarkSummary(weightedScore, modules) {
    let summary;

    if (weightedScore >= 90) {
      summary = 'Browser protection performed strongly across most benchmark modules.';
    } else if (weightedScore >= 75) {
      summary = 'Browser protection performed well overall, but a few categories were still reachable.';
    } else if (weightedScore >= 50) {
      summary = 'Browser protection provided partial coverage. Several ad/tracker categories were reachable.';
    } else {
      summary = 'Browser protection showed weak coverage in this benchmark.';
    }

    const weakestModule = modules
      .filter((moduleScore) => moduleScore.total > 0)
      .sort((a, b) => a.score - b.score)[0];

    return weakestModule
      ? `${summary} Weakest module: ${weakestModule.name}.`
      : summary;
  }

  function getDiagnosticsSummary() {
    return testRows
      .map((row) => getRowDiagnostic(row))
      .filter(Boolean)
      .map((diagnostic) => ({
        label: diagnostic.label,
        category: diagnostic.category,
        group: diagnostic.group,
        type: diagnostic.type,
        status: diagnostic.status,
        outcome: diagnostic.outcome,
        confidence: diagnostic.confidence,
        durationMs: diagnostic.durationMs,
        reason: diagnostic.reason,
      }));
  }

  function getBenchmarkReport() {
    const moduleScores = getModuleScores();
    const generatedAt = new Date();

    return {
      benchmarkName: 'Super Adblock Test',
      generatedAt: generatedAt.toISOString(),
      domainListReviewedAt: DOMAIN_LIST_REVIEWED_AT,
      methodologyNote: DOMAIN_LIST_NOTE,
      uniqueHostTests: getUniqueHostCount(),
      userAgent: navigator.userAgent,
      pageUrl: window.location.href,
      testedBrowserProtection: 'Built-in content blocker',
      overallProtectionScore: getWeightedOverallScore(),
      rawBlockRate: getRawOverallScore(),
      totalTests: state.total,
      blockedTests: state.blocked,
      accessibleTests: state.accessible,
      baselineStatus: getBaselineStatusForReport(),
      moduleScores,
      categoryScores: getCategoryBreakdown(),
      topMissedAreas: getTopMissedCategories(),
      interpretation: getBenchmarkSummary(getWeightedOverallScore(), moduleScores),
      diagnosticsSummary: getDiagnosticsSummary(),
    };
  }

  function getSummaryText() {
    const strongest = getStrongestModule();
    const weakest = getWeakestModule();
    const missed = getTopMissedCategories()
      .map((category) => category.category)
      .join(', ');
    const baselineStatus = getBaselineStatusForReport();
    const lines = [
      'Super Adblock Test Result',
      `Overall Protection Score: ${getWeightedOverallScore()}%`,
      `Raw Block Rate: ${getRawOverallScore()}%`,
      `Strongest Area: ${strongest ? strongest.name : 'Unavailable'}`,
      `Weakest Area: ${weakest ? weakest.name : 'Unavailable'}`,
      `Top Missed Areas: ${missed || 'No major weak areas detected'}`,
    ];

    if (baselineStatus) {
      lines.push(`Baseline: ${baselineStatus}`);
    }

    lines.push(
      'Tested Browser Protection: Built-in content blocker',
      `Tested At: ${formatReportDate()}`,
    );

    return lines.join('\n');
  }

  function setBenchmarkActionMessage(message) {
    const el = document.getElementById('benchmarkActionMessage');
    if (el) el.textContent = message;
  }

  async function copySummary() {
    if (state.checked < state.total) {
      setBenchmarkActionMessage('Run the test first.');
      return;
    }

    try {
      await navigator.clipboard.writeText(getSummaryText());
      setBenchmarkActionMessage('Summary copied.');
    } catch (error) {
      console.error('Failed to copy benchmark summary:', error);
      setBenchmarkActionMessage('Copy failed.');
    }
  }

  function getReportFilename(date = new Date()) {
    const pad = (value) => String(value).padStart(2, '0');

    return (
      'adblock-benchmark-report-' +
      `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}-` +
      `${pad(date.getHours())}-${pad(date.getMinutes())}.json`
    );
  }

  function downloadJsonReport() {
    if (state.checked < state.total) {
      setBenchmarkActionMessage('Run the test first.');
      return;
    }

    const blob = new Blob([JSON.stringify(getBenchmarkReport(), null, 2)], {
      type: 'application/json',
    });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');

    link.href = url;
    link.download = getReportFilename();
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
    setBenchmarkActionMessage('JSON report exported.');
  }

  function ensureProtectionPassportModal() {
    let modal = document.getElementById('protectionPassportModal');
    if (modal) return modal;

    modal = document.createElement('div');
    modal.className = 'modal fade';
    modal.id = 'protectionPassportModal';
    modal.tabIndex = -1;
    modal.setAttribute('aria-labelledby', 'protectionPassportTitle');
    modal.setAttribute('aria-hidden', 'true');

    modal.innerHTML =
      '<div class="modal-dialog modal-dialog-scrollable">' +
      '<div class="modal-content statusDetailsContent">' +
      '<div class="modal-header">' +
      '<h6 class="modal-title" id="protectionPassportTitle">Protection Passport</h6>' +
      '<button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="Close"></button>' +
      '</div>' +
      '<div class="modal-body" id="protectionPassportBody"></div>' +
      '</div></div>';

    document.body.appendChild(modal);

    return modal;
  }

  function showProtectionPassport() {
    if (state.checked < state.total) {
      setBenchmarkActionMessage('Run the test first.');
      return;
    }

    const modal = ensureProtectionPassportModal();
    const body = document.getElementById('protectionPassportBody');
    const strongest = getStrongestModule();
    const weakest = getWeakestModule();
    const baselineStatus = getBaselineStatusForReport() || 'Not included';

    if (body) {
      body.innerHTML =
        '<div class="protectionPassportCard">' +
        '<h5>Super Adblock Test</h5>' +
        `<strong class="passportScore">Overall Protection: ${getWeightedOverallScore()}%</strong>` +
        `<div class="passportMeta">Raw Block Rate: ${getRawOverallScore()}%</div>` +
        `<div class="passportMeta">Strongest Area: ${escapeHtml(strongest ? strongest.name : 'Unavailable')}</div>` +
        `<div class="passportMeta">Weakest Area: ${escapeHtml(weakest ? weakest.name : 'Unavailable')}</div>` +
        `<div class="passportMeta">Baseline: ${escapeHtml(baselineStatus)}</div>` +
        `<div class="passportMeta">Confidence: ${escapeHtml(getResultConfidence())}</div>` +
        `<div class="passportMeta">Tested At: ${escapeHtml(formatPassportDate())}</div>` +
        '<div class="diagnosticActions mt-3">' +
        '<button type="button" class="diagnosticActionBtn" id="passportCopySummary">Copy Summary</button>' +
        '<button type="button" class="diagnosticActionBtn" data-bs-dismiss="modal">Close</button>' +
        '</div>' +
        '</div>';
    }

    const copyButton = document.getElementById('passportCopySummary');
    if (copyButton) {
      copyButton.addEventListener('click', copySummary);
    }

    if (typeof bootstrap !== 'undefined') {
      new bootstrap.Modal(modal).show();
    }
  }

  function ensureProtectionInfoModal() {
    let modal = document.getElementById('protectionInfoModal');
    if (modal) return modal;

    modal = document.createElement('div');
    modal.className = 'modal fade';
    modal.id = 'protectionInfoModal';
    modal.tabIndex = -1;
    modal.setAttribute('aria-labelledby', 'protectionInfoTitle');
    modal.setAttribute('aria-hidden', 'true');

    modal.innerHTML =
      '<div class="modal-dialog modal-dialog-scrollable">' +
      '<div class="modal-content statusDetailsContent">' +
      '<div class="modal-header">' +
      '<h6 class="modal-title" id="protectionInfoTitle">About This Test</h6>' +
      '<button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="Close"></button>' +
      '</div>' +
      '<div class="modal-body">' +
      '<div class="protectionInfoList">' +
      '<div class="protectionInfoItem">' +
      '<span class="info-icon"><i class="fa-solid fa-shield-halved" aria-hidden="true"></i></span>' +
      '<div><h5>What we test</h5><p>We probe known ad, analytics, social, error tracking, mixed tracker, and OEM vendor hosts using the current network path.</p></div>' +
      '</div>' +
      '<div class="protectionInfoItem">' +
      '<span class="info-icon"><i class="fa-solid fa-circle-check" aria-hidden="true"></i></span>' +
      '<div><h5>How to read it</h5><p>Blocked means the request failed or was intercepted. Accessible means the host looked reachable from this browser.</p></div>' +
      '</div>' +
      '<div class="protectionInfoItem">' +
      '<span class="info-icon"><i class="fa-solid fa-triangle-exclamation" aria-hidden="true"></i></span>' +
      '<div><h5>Remember</h5><p>A perfect score here is a strong signal, but real-world blocking still depends on filter lists, apps, VPN settings, and DNS rules.</p></div>' +
      '</div>' +
      '</div>' +
      '</div>' +
      '</div></div>';

    document.body.appendChild(modal);

    return modal;
  }

  function showProtectionInfo() {
    const modal = ensureProtectionInfoModal();

    if (typeof bootstrap !== 'undefined') {
      new bootstrap.Modal(modal).show();
    }
  }

  function showDetailedResults() {
    const resultsSection = document.querySelector('.results-section');
    if (!resultsSection) return;

    document.querySelectorAll('.rowDiv.d-none').forEach((rowDiv) => {
      rowDiv.classList.remove('d-none');

      const section = rowDiv.closest('.grid');
      const status = section ? section.querySelector('.toggle-button .status') : null;
      const svg = section ? section.querySelector('.toggle-button .svg') : null;

      if (status) status.textContent = 'Collapse';
      if (svg) svg.innerHTML = icons.collapse;
    });

    resultsSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  function ensureBenchmarkDashboard() {
    let dashboard = document.getElementById('benchmarkDashboard');
    if (dashboard) return dashboard;

    const section = document.getElementById('diagnosticReportSection');
    if (section) {
      dashboard = document.createElement('div');
      dashboard.id = 'benchmarkDashboard';
      dashboard.className = 'benchmarkDashboard';
      section.appendChild(dashboard);
    }

    return dashboard;
  }

  function renderBenchmarkDashboard() {
    const dashboard = ensureBenchmarkDashboard();
    if (!dashboard) return;

    const existingDetails = document.getElementById('benchmarkDetails');
    const detailsWereOpen = Boolean(
      existingDetails && existingDetails.classList.contains('is-open'),
    );
    const moduleScores = getModuleScores();
    const categories = getCategoryBreakdown();
    const topMissed = getTopMissedCategories();
    const weightedScore = getWeightedOverallScore();
    const rawScore = getRawOverallScore();
    const reportsReady = state.total > 0 && state.checked >= state.total;
    const disabledAttr = reportsReady ? '' : ' disabled';
    const moduleHtml = moduleScores.length
      ? moduleScores
          .map((moduleScore) => {
            const strength = getStrengthLabel(moduleScore.score);

            return (
              '<li>' +
              `<strong>${escapeHtml(moduleScore.name)}</strong> - ` +
              `${moduleScore.score}% - ${moduleScore.blocked}/${moduleScore.total} blocked - ` +
              `<span class="strength${strength}">${strength}</span>` +
              '</li>'
            );
          })
          .join('')
      : '<li>No module scores available yet.</li>';
    const categoryHtml = categories.length
      ? categories
          .map(
            (category) =>
              `<li><strong>${escapeHtml(category.category)}</strong> - ${category.score}% - ${category.blocked}/${category.total} blocked</li>`,
          )
          .join('')
      : '<li>No category scores available yet.</li>';
    const weakAreas =
      topMissed.length && topMissed.some((category) => category.score < 85)
        ? topMissed
            .map(
              (category, index) =>
                `<li class="benchmarkWeakArea">${index + 1}. ${escapeHtml(category.category)} - ${category.score}% blocked</li>`,
            )
            .join('')
        : '<li>No major weak areas detected in this run.</li>';

    dashboard.innerHTML =
      '<div class="benchmarkCompactSummary">' +
      '<div class="benchmarkGrid">' +
      '<div class="benchmarkCard">' +
      '<span class="benchmarkLabel">Overall Protection Score</span>' +
      `<strong class="benchmarkScore">${weightedScore}%</strong>` +
      '</div>' +
      '<div class="benchmarkCard">' +
      '<span class="benchmarkLabel">Raw Block Rate</span>' +
      `<strong class="benchmarkScore">${rawScore}%</strong>` +
      `<span>${state.blocked}/${state.total} blocked</span>` +
      '</div>' +
      '<div class="benchmarkCard">' +
      '<span class="benchmarkLabel">Coverage</span>' +
      `<strong class="benchmarkScore">${getUniqueHostCount()}</strong>` +
      `<span>unique host probes - reviewed ${escapeHtml(DOMAIN_LIST_REVIEWED_AT)}</span>` +
      '</div>' +
      '<div class="benchmarkCard">' +
      '<span class="benchmarkLabel">Method</span>' +
      '<strong class="benchmarkScore">GET</strong>' +
      '<span>GET no-cors host checks across representative ad, tracker, telemetry, affiliate, consent, and video endpoints</span>' +
      '</div>' +
      '</div>' +
      `<p class="benchmarkSummary">${escapeHtml(getBenchmarkSummary(weightedScore, moduleScores))}</p>` +
      `<p class="benchmarkMethodNote">${escapeHtml(DOMAIN_LIST_NOTE)}</p>` +
      '<div class="benchmarkActions">' +
      '<button type="button" id="benchmarkDetailsToggle" class="benchmarkActionBtn">' +
      `${detailsWereOpen ? 'Hide detailed results' : 'View detailed results'}` +
      '</button>' +
      `<button type="button" class="benchmarkActionBtn" id="passportBtn"${disabledAttr}>View Protection Passport</button>` +
      '<span id="benchmarkActionMessage" class="benchmarkActionMessage"></span>' +
      '</div>' +
      '</div>' +
      `<div id="benchmarkDetails" class="benchmarkDetails${detailsWereOpen ? ' is-open' : ''}">` +
      '<div class="benchmarkCard">' +
      '<h4>Module Breakdown</h4>' +
      `<ul class="benchmarkBreakdownList">${moduleHtml}</ul>` +
      '</div>' +
      '<div class="benchmarkCard">' +
      '<h4>Category Breakdown</h4>' +
      `<ul class="benchmarkBreakdownList">${categoryHtml}</ul>` +
      '</div>' +
      '<div class="benchmarkCard">' +
      '<h4>Top Missed Areas</h4>' +
      `<ul class="benchmarkBreakdownList">${weakAreas}</ul>` +
      '</div>' +
      '</div>';

    const toggle = document.getElementById('benchmarkDetailsToggle');
    const details = document.getElementById('benchmarkDetails');

    if (toggle && details) {
      toggle.addEventListener('click', () => {
        const isOpen = details.classList.toggle('is-open');
        toggle.textContent = isOpen
          ? 'Hide detailed results'
          : 'View detailed results';

        if (isOpen) {
          details.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        }
      });
    }

    const passportButton = document.getElementById('passportBtn');

    if (passportButton) {
      passportButton.addEventListener('click', showProtectionPassport);
    }
  }

  function updateAllSummaries(final = false) {
    updateSummary(final);
    updateProgressUi(final);
  }

  async function runWithConcurrency(tasks, limit) {
    const results = [];
    let index = 0;
    const workerCount = Math.min(limit, tasks.length);

    async function worker() {
      while (index < tasks.length) {
        const currentIndex = index++;
        results[currentIndex] = await tasks[currentIndex]();
      }
    }

    await Promise.all(Array.from({ length: workerCount }, worker));

    return results;
  }

  function addResult(row, blocked) {
    state.checked += 1;

    if (blocked) state.blocked += 1;
    else state.accessible += 1;

    addCategoryResult(row.dataset.category || 'Uncategorized', blocked);
    updateAllSummaries(false);
  }

  async function runDiagnosticForRow(row) {
    const label = row.dataset.label || '';
    const type = row.dataset.type || 'host';
    const config = getRowTestConfig(row);

    if (type === 'cosmetic') {
      return runCosmeticDiagnostic(config);
    }

    if (type === 'script-path') {
      return runScriptPathDiagnostic(config, label);
    }

    return runHostDiagnostic(label);
  }

  async function runAndApplyRowTest(row, countInitial = false) {
    let diagnostic;

    try {
      diagnostic = await runDiagnosticForRow(row);
    } catch (error) {
      const type = row.dataset.type || 'host';

      diagnostic = {
        label: row.dataset.label || '',
        category: row.dataset.category || '',
        group: row.dataset.group || '',
        type,
        status: 'blocked',
        blocked: true,
        method: null,
        fetchMode: null,
        pathsTested: [],
        attemptedUrls: [],
        attempts: [],
        startedAt: isoNow(),
        endedAt: isoNow(),
        durationMs: 0,
        outcome: 'test-error',
        responseType: null,
        errorName: error && error.name ? error.name : 'Error',
        errorMessage: error && error.message ? error.message : '',
        reason: 'The test failed unexpectedly before completion.',
        confidence: 'low',
      };
    }

    setRowDiagnostic(row, diagnostic);
    setRowStatus(row, diagnostic.blocked, diagnostic.status);

    if (countInitial) {
      addResult(row, diagnostic.blocked);
    }

    return diagnostic;
  }

  function buildTasks() {
    const wrapper = document.getElementById('test');
    if (!wrapper) return [];

    wrapper.innerHTML = '';
    testRows.length = 0;

    const tasks = [];

    Object.entries(HOST_TESTS).forEach(([categoryName, category]) => {
      const section = createSection(categoryName);
      if (!section) return;

      Object.entries(category).forEach(([groupName, hosts]) => {
        const listEl = createGroup(section, groupName);

        unique(hosts).forEach((host) => {
          registerCategoryTest(categoryName);

          const row = createRow(host, listEl, {
            category: categoryName,
            group: groupName,
            type: 'host',
          });

          tasks.push(async () => {
            const diagnostic = await runAndApplyRowTest(row, true);

            return diagnostic;
          });
        });
      });
    });

    return tasks;
  }

  function getRowsByStatus(statusClass) {
    return [...document.querySelectorAll(`.adblockHostDiv.${statusClass} span`)]
      .map((span) => span.textContent.trim())
      .filter(Boolean);
  }

  function ensureStatusDetailsModal() {
    let modal = document.getElementById('statusDetailsModal');
    if (modal) return modal;

    modal = document.createElement('div');
    modal.className = 'modal fade';
    modal.id = 'statusDetailsModal';
    modal.tabIndex = -1;
    modal.setAttribute('aria-labelledby', 'statusDetailsTitle');
    modal.setAttribute('aria-hidden', 'true');

    modal.innerHTML =
      '<div class="modal-dialog modal-dialog-scrollable">' +
      '<div class="modal-content statusDetailsContent">' +
      '<div class="modal-header">' +
      '<h6 class="modal-title" id="statusDetailsTitle">Tests</h6>' +
      '<button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="Close"></button>' +
      '</div>' +
      '<div class="modal-body" id="statusDetailsBody"></div>' +
      '</div></div>';

    document.body.appendChild(modal);

    return modal;
  }

  function showStatusDetails(statusType) {
    const blocked = statusType === 'blocked';
    const rows = blocked
      ? getRowsByStatus('blockedGreen')
      : getRowsByStatus('blockedRed');
    const modal = ensureStatusDetailsModal();
    const body = document.getElementById('statusDetailsBody');
    const heading = document.getElementById('statusDetailsTitle');

    if (heading) {
      heading.textContent = `${blocked ? 'Blocked Tests' : 'Reachable Tests'} (${rows.length})`;
    }

    if (body) {
      body.innerHTML = rows.length
        ? `<ul class="statusHostList">${rows.map((row) => `<li>${escapeHtml(row)}</li>`).join('')}</ul>`
        : '<p class="statusEmpty mb-0">No items found.</p>';
    }

    if (typeof bootstrap !== 'undefined') {
      new bootstrap.Modal(modal).show();
    }
  }

  function ensureDiagnosticStyles() {
    return;
  }

  function ensureDiagnosticModal() {
    let modal = document.getElementById('diagnosticDetailsModal');
    if (modal) return modal;

    ensureDiagnosticStyles();

    modal = document.createElement('div');
    modal.className = 'modal fade';
    modal.id = 'diagnosticDetailsModal';
    modal.tabIndex = -1;
    modal.setAttribute('aria-labelledby', 'diagnosticDetailsTitle');
    modal.setAttribute('aria-hidden', 'true');

    modal.innerHTML =
      '<div class="modal-dialog modal-dialog-scrollable">' +
      '<div class="modal-content statusDetailsContent">' +
      '<div class="modal-header">' +
      '<h6 class="modal-title" id="diagnosticDetailsTitle">Test Diagnostic Details</h6>' +
      '<button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="Close"></button>' +
      '</div>' +
      '<div class="modal-body" id="diagnosticDetailsBody"></div>' +
      '</div></div>';

    document.body.appendChild(modal);

    return modal;
  }

  function diagnosticValue(value) {
    if (value === null || value === undefined || value === '') return 'Unavailable';
    return escapeHtml(value);
  }

  function diagnosticList(items) {
    if (!items || !items.length) return 'Unavailable';

    return `<ul class="statusHostList">${items
      .map((item) => `<li>${escapeHtml(item)}</li>`)
      .join('')}</ul>`;
  }

  function getWhatHappenedText(diagnostic) {
    if (diagnostic.outcome === 'timeout') {
      return 'The request timed out before completion. This may happen because of blocking, slow network, DNS issues, or browser/network protection.';
    }

    if (diagnostic.responseType === 'opaque') {
      return 'The request resolved as an opaque response. This means the browser reached the resource, but does not expose status/body details because it is cross-origin.';
    }

    if (diagnostic.status === 'accessible') {
      return 'This test resource was reachable. Browser protection did not stop this request. This does not always mean a visible ad appeared, but it means this network request/category was allowed.';
    }

    return 'This test did not complete successfully or the bait element was hidden. This usually means built-in content blocker rules, browser protection, or private browser protection stopped it.';
  }

  function getNetworkDetailsHtml(diagnostic) {
    if (diagnostic.type === 'cosmetic') {
      const visibility = diagnostic.visibility || {};

      return (
        '<div class="diagnosticGrid">' +
        `<div class="diagnosticCard"><span class="diagnosticLabel">Element classes tested</span>${diagnosticValue(diagnostic.testedClasses)}</div>` +
        `<div class="diagnosticCard"><span class="diagnosticLabel">Visibility result</span>${diagnosticValue(diagnostic.outcome)}</div>` +
        `<div class="diagnosticCard"><span class="diagnosticLabel">Display</span>${diagnosticValue(visibility.display)}</div>` +
        `<div class="diagnosticCard"><span class="diagnosticLabel">Visibility</span>${diagnosticValue(visibility.visibility)}</div>` +
        `<div class="diagnosticCard"><span class="diagnosticLabel">Opacity</span>${diagnosticValue(visibility.opacity)}</div>` +
        `<div class="diagnosticCard"><span class="diagnosticLabel">Size</span>${diagnosticValue(`${visibility.width || 0} x ${visibility.height || 0}`)}</div>` +
        `<div class="diagnosticCard"><span class="diagnosticLabel">In DOM</span>${diagnosticValue(String(Boolean(visibility.isInDOM)))}</div>` +
        `<div class="diagnosticCard"><span class="diagnosticLabel">Duration</span>${diagnosticValue(`${diagnostic.durationMs}ms`)}</div>` +
        '</div>'
      );
    }

    return (
      '<div class="diagnosticGrid">' +
      `<div class="diagnosticCard"><span class="diagnosticLabel">Method</span>${diagnosticValue(diagnostic.method)}</div>` +
      `<div class="diagnosticCard"><span class="diagnosticLabel">Fetch mode</span>${diagnosticValue(diagnostic.fetchMode)}</div>` +
      `<div class="diagnosticCard"><span class="diagnosticLabel">Attempted URL(s)</span>${diagnosticList(diagnostic.attemptedUrls)}</div>` +
      `<div class="diagnosticCard"><span class="diagnosticLabel">Paths tested</span>${diagnosticList(diagnostic.pathsTested)}</div>` +
      `<div class="diagnosticCard"><span class="diagnosticLabel">Duration</span>${diagnosticValue(`${diagnostic.durationMs}ms`)}</div>` +
      `<div class="diagnosticCard"><span class="diagnosticLabel">Response type</span>${diagnosticValue(diagnostic.responseType)}</div>` +
      `<div class="diagnosticCard"><span class="diagnosticLabel">Error name</span>${diagnosticValue(diagnostic.errorName)}</div>` +
      `<div class="diagnosticCard"><span class="diagnosticLabel">Error message</span>${diagnosticValue(diagnostic.errorMessage)}</div>` +
      '</div>'
    );
  }

  function getCompactDiagnostic(diagnostic) {
    return {
      label: diagnostic.label,
      category: diagnostic.category,
      group: diagnostic.group,
      type: diagnostic.type,
      status: diagnostic.status,
      method: diagnostic.method,
      fetchMode: diagnostic.fetchMode,
      pathsTested: diagnostic.pathsTested,
      attemptedUrls: diagnostic.attemptedUrls,
      startedAt: diagnostic.startedAt,
      endedAt: diagnostic.endedAt,
      durationMs: diagnostic.durationMs,
      outcome: diagnostic.outcome,
      responseType: diagnostic.responseType,
      errorName: diagnostic.errorName,
      errorMessage: diagnostic.errorMessage,
      reason: diagnostic.reason,
      confidence: diagnostic.confidence,
      attempts: diagnostic.attempts,
      testedClasses: diagnostic.testedClasses,
      visibility: diagnostic.visibility,
    };
  }

  function renderDiagnosticModal(row) {
    ensureDiagnosticModal();
    const body = document.getElementById('diagnosticDetailsBody');
    const diagnostic = getRowDiagnostic(row);

    if (!body || !diagnostic) return;

    const rawDebug = escapeHtml(
      JSON.stringify(getCompactDiagnostic(diagnostic), null, 2),
    );
    const statusClass = diagnostic.status;
    const statusText =
      diagnostic.status === 'blocked' ? 'Blocked' : 'Accessible';

    body.innerHTML =
      '<div class="diagnosticActions">' +
      '<button type="button" class="diagnosticActionBtn" id="copyDiagnosticLabel">Copy tested item</button>' +
      '<button type="button" class="diagnosticActionBtn" id="retestDiagnosticRow">Retest this item</button>' +
      '</div>' +
      '<h6>Summary</h6>' +
      '<div class="diagnosticGrid">' +
      `<div class="diagnosticCard"><span class="diagnosticLabel">Status</span><span class="statusPill ${statusClass}">${statusText}</span></div>` +
      `<div class="diagnosticCard"><span class="diagnosticLabel">Tested item</span>${diagnosticValue(diagnostic.label)}</div>` +
      `<div class="diagnosticCard"><span class="diagnosticLabel">Category</span>${diagnosticValue(diagnostic.category)}</div>` +
      `<div class="diagnosticCard"><span class="diagnosticLabel">Group</span>${diagnosticValue(diagnostic.group)}</div>` +
      `<div class="diagnosticCard"><span class="diagnosticLabel">Test type</span>${diagnosticValue(diagnostic.type)}</div>` +
      '</div>' +
      '<h6>Network/Test Details</h6>' +
      getNetworkDetailsHtml(diagnostic) +
      '<h6>What happened</h6>' +
      `<p class="diagnosticExplanation ${statusClass}">${escapeHtml(getWhatHappenedText(diagnostic))}</p>` +
      '<h6>Confidence</h6>' +
      '<div class="diagnosticGrid">' +
      `<div class="diagnosticCard"><span class="diagnosticLabel">Confidence</span>${diagnosticValue(diagnostic.confidence)}</div>` +
      `<div class="diagnosticCard"><span class="diagnosticLabel">Reason</span>${diagnosticValue(diagnostic.reason)}</div>` +
      '</div>' +
      '<details>' +
      '<summary class="diagnosticDetailsSummary">Raw debug details</summary>' +
      `<pre class="rawDebugBlock">${rawDebug}</pre>` +
      '</details>';

    const copyButton = document.getElementById('copyDiagnosticLabel');
    if (copyButton) {
      copyButton.addEventListener('click', () => {
        if (navigator.clipboard) {
          navigator.clipboard
            .writeText(row.dataset.label || diagnostic.label || '')
            .catch(console.error);
        }
      });
    }

    const retestButton = document.getElementById('retestDiagnosticRow');
    if (retestButton) {
      retestButton.addEventListener('click', () => retestRow(row));
    }
  }

  function showDiagnosticModal(row) {
    const modal = ensureDiagnosticModal();

    renderDiagnosticModal(row);

    if (typeof bootstrap !== 'undefined') {
      new bootstrap.Modal(modal).show();
    }
  }

  async function retestRow(row) {
    const previousStatus = row.dataset.status;
    const retestButton = document.getElementById('retestDiagnosticRow');

    if (retestButton) {
      retestButton.setAttribute('disabled', 'disabled');
      retestButton.textContent = 'Retesting...';
    }

    const diagnostic = await runAndApplyRowTest(row, false);
    const nextStatus = diagnostic.status;

    if (previousStatus !== nextStatus) {
      if (previousStatus === 'accessible') state.accessible -= 1;
      if (previousStatus === 'blocked') state.blocked -= 1;
      if (nextStatus === 'accessible') state.accessible += 1;
      if (nextStatus === 'blocked') state.blocked += 1;
      adjustCategoryResult(
        row.dataset.category || 'Uncategorized',
        previousStatus,
        nextStatus,
      );

      updateAllSummaries(true);
      bindStatTriggers();
    }

    renderDiagnosticModal(row);
  }

  function bindStatTriggers() {
    document.querySelectorAll('.statTrigger').forEach((trigger) => {
      const statusType = trigger.getAttribute('data-status');

      trigger.addEventListener('click', () => showStatusDetails(statusType));

      trigger.addEventListener('keydown', (event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          showStatusDetails(statusType);
        }
      });
    });
  }

  function bindFinalEvents() {
    document.querySelectorAll('.adblockHostDiv').forEach((row) => {
      const textSpan = row.querySelector('span');
      if (!textSpan) return;

      row.addEventListener('click', () => {
        showDiagnosticModal(row);
      });

      row.addEventListener('keydown', (event) => {
        if (event.key !== 'Enter' && event.key !== ' ') return;

        event.preventDefault();
        row.click();
      });

      const detailsButton = row.querySelector('.diagnosticDetailsBtn');
      if (detailsButton) {
        detailsButton.addEventListener('click', (event) => {
          event.stopPropagation();
          showDiagnosticModal(row);
        });
      }
    });

    bindStatTriggers();
  }

  async function startAdBlockTesting() {
    if (state.isRunning) return;

    ensureDiagnosticStyles();
    state.isRunning = true;
    setPageState('running');
    setButtonState('Testing...', {
      disabled: true,
      ariaLabel: 'Ad block test is running',
      icon: 'fa-solid fa-spinner',
    });

    state.total = countHostTests();
    state.checked = 0;
    state.blocked = 0;
    state.accessible = 0;
    resetCategoryStats();

    updateSummary(false);

    await runWithConcurrency(buildTasks(), MAX_CONCURRENT_TESTS);

    updateSummary(true);
    updateProgressUi(true);
    state.isRunning = false;
    revealCompletedRows();
    setPageState('complete');
    if (typeof window.superAdblockTrack === 'function') {
      window.superAdblockTrack('Test Complete', {
        score: String(getPercentage()),
        blocked: String(state.blocked),
        reachable: String(state.accessible),
        total: String(state.total)
      });
    }
    bindFinalEvents();
    setButtonState('Re-test', {
      disabled: false,
      ariaLabel: 'Run the ad block test again',
      icon: 'fa-solid fa-rotate-right',
    });
  }

  window.softReload = function softReload() {
    location.reload();
  };

  function initializeStartButton() {
    setPageState('idle');
    setButtonState('Start test', {
      disabled: false,
      ariaLabel: 'Start the ad block test',
      icon: 'fa-solid fa-play',
    });
    updateProgressUi(false);

    const retest = document.getElementById('retest');
    if (retest) {
      retest.addEventListener('click', startAdBlockTesting);
    }

    const resultsLink = document.querySelector('.results-link');
    if (resultsLink) {
      resultsLink.addEventListener('click', (event) => {
        event.preventDefault();
        showDetailedResults();
      });
    }

    const protectionInfoButton = document.getElementById('protectionInfoBtn');
    if (protectionInfoButton) {
      protectionInfoButton.addEventListener('click', showProtectionInfo);
    }

    const query = new URLSearchParams(window.location.search);
    if (query.get('run') === '1') {
      history.replaceState(null, '', `${window.location.pathname}${window.location.hash}`);
      window.requestAnimationFrame(() => {
        startAdBlockTesting();
      });
    }
  }

  document.addEventListener('DOMContentLoaded', initializeStartButton);
})();
