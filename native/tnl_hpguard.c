/*
 * TNL850 星渊护血内核（原生层）—— 不依赖 jni.h / Windows SDK
 * ------------------------------------------------------------------
 * x64 下 __stdcall 被忽略 ⇒ JNI 符号名不修饰，直接与 JVM 对接。
 * 职责：限伤上限与回弹、无敌窗口、权威血量、握手/自检。
 */
typedef int            jint;
typedef long long      jlong;
typedef signed char    jbyte;
typedef void*          JNIEnvP;
typedef void*          jclassP;
#define JNIEXPORT __declspec(dllexport)
#define JNICALL __stdcall

#define TNL_MAGIC 0x544E4C31L
#define TNL_ABI   1
#define TNL_SLOTS 32

static long long g_health[TNL_SLOTS];
static long long g_max[TNL_SLOTS];
static long long g_cap[TNL_SLOTS];
static int       g_invuln[TNL_SLOTS];
static int       g_ready[TNL_SLOTS];
static long long g_ops = 0;

static int slot_ok(int s) { return s >= 0 && s < TNL_SLOTS; }

JNIEXPORT jlong JNICALL
Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_AstralVitalsKernel_nativeHandshake(JNIEnvP e, jclassP c) {
    (void)e; (void)c;
    return ((jlong)TNL_MAGIC << 8) | (jlong)TNL_ABI;
}

JNIEXPORT jlong JNICALL
Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_AstralVitalsKernel_nativeSelfCheck(JNIEnvP e, jclassP c) {
    long long acc = (long long)TNL_MAGIC; int i;
    (void)e; (void)c;
    for (i = 0; i < TNL_SLOTS; ++i)
        acc = (acc * 131 + g_health[i] + g_max[i] + g_cap[i] + g_invuln[i] + g_ready[i]) & 0x7FFFFFFFFFFFFFFFLL;
    return (jlong)(acc ^ (g_ops * 7919));
}

JNIEXPORT void JNICALL
Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_AstralVitalsKernel_nativeInit(
        JNIEnvP e, jclassP c, jint slot, jlong maxMilli, jlong capMilli, jint invulnTicks) {
    (void)e; (void)c;
    if (!slot_ok(slot)) return;
    g_max[slot] = maxMilli; g_health[slot] = maxMilli; g_cap[slot] = capMilli;
    g_invuln[slot] = 0; g_ready[slot] = 1; g_ops++;
}

JNIEXPORT jlong JNICALL
Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_AstralVitalsKernel_nativeRecord(
        JNIEnvP e, jclassP c, jint slot, jlong healthMilli, jint ticks) {
    long long cap, lowest;
    (void)e; (void)c; (void)ticks;
    if (!slot_ok(slot) || !g_ready[slot]) return healthMilli;
    g_ops++;
    if (g_invuln[slot] > 0) g_invuln[slot]--;
    cap = g_cap[slot];
    lowest = g_health[slot] - cap;
    if (lowest < 0) lowest = 0;
    if (healthMilli < lowest) g_health[slot] = lowest; else g_health[slot] = healthMilli;
    return (jlong)g_health[slot];
}

JNIEXPORT jlong JNICALL
Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_AstralVitalsKernel_nativeRequestDamage(
        JNIEnvP e, jclassP c, jint slot, jlong amountMilli, jint ticks) {
    (void)e; (void)c; (void)ticks;
    if (!slot_ok(slot) || !g_ready[slot]) return amountMilli > 0 ? amountMilli : 0;
    g_ops++;
    if (g_invuln[slot] > 0) return 0;
    if (amountMilli > g_cap[slot]) return g_cap[slot];
    return amountMilli > 0 ? amountMilli : 0;
}

JNIEXPORT void JNICALL
Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_AstralVitalsKernel_nativeApply(
        JNIEnvP e, jclassP c, jint slot, jlong amountMilli, jint invulnTicks) {
    (void)e; (void)c;
    if (!slot_ok(slot) || !g_ready[slot]) return;
    g_ops++;
    g_health[slot] -= amountMilli;
    if (g_health[slot] < 0) g_health[slot] = 0;
    g_invuln[slot] = invulnTicks;
}

JNIEXPORT jlong JNICALL
Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_AstralVitalsKernel_nativeHealth(JNIEnvP e, jclassP c, jint slot) {
    (void)e; (void)c;
    if (!slot_ok(slot) || !g_ready[slot]) return -1;
    g_ops++;
    return (jlong)g_health[slot];
}
