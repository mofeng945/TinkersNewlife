/* TNL852 星渊护血内核（原生层）★ 名字已抹 ✗ 不含任何可猜关键词 */
typedef int       jint;
typedef long long jlong;
typedef void*     JNIEnvP;
typedef void*     jclassP;
#define JNIEXPORT __declspec(dllexport)
#define JNICALL __stdcall
#define TNL_MAGIC 0x544E4C31L
#define TNL_TOKEN 0x1A2B3C4DL
#define TNL_ABI   2
#define TNL_SLOTS 32
static long long g_h[TNL_SLOTS], g_m[TNL_SLOTS], g_c[TNL_SLOTS];
static int g_v[TNL_SLOTS], g_r[TNL_SLOTS];
static long long g_ops = 0;
static int ok(int s){return s>=0&&s<TNL_SLOTS;}
JNIEXPORT jlong JNICALL Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_Vq_k(JNIEnvP e,jclassP c){
    (void)e;(void)c; return ((jlong)TNL_MAGIC<<8)|(jlong)TNL_ABI;}
JNIEXPORT jlong JNICALL Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_Vq_t(JNIEnvP e,jclassP c){
    (void)e;(void)c; return (jlong)(TNL_TOKEN ^ (g_ops*7919));}
JNIEXPORT jlong JNICALL Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_Vq_s(JNIEnvP e,jclassP c){
    long long a=(long long)TNL_MAGIC; int i; (void)e;(void)c;
    for(i=0;i<TNL_SLOTS;++i) a=(a*131+g_h[i]+g_m[i]+g_c[i]+g_v[i]+g_r[i])&0x7FFFFFFFFFFFFFFFLL;
    return (jlong)(a^(g_ops*7919));}
JNIEXPORT void JNICALL Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_Vq_i(JNIEnvP e,jclassP c,jint s,jlong m,jlong cp,jint v){
    (void)e;(void)c; if(!ok(s))return; g_m[s]=m; g_h[s]=m; g_c[s]=cp; g_v[s]=0; g_r[s]=1; g_ops++;}
JNIEXPORT jlong JNICALL Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_Vq_r(JNIEnvP e,jclassP c,jint s,jlong now,jint tk){
    long long low; (void)e;(void)c;(void)tk;
    if(!ok(s)||!g_r[s]) return now; g_ops++;
    if(g_v[s]>0) g_v[s]--;
    low=g_h[s]-g_c[s]; if(low<0) low=0;
    g_h[s]= now<low? low: now; return (jlong)g_h[s];}
JNIEXPORT jlong JNICALL Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_Vq_d(JNIEnvP e,jclassP c,jint s,jlong amt,jint tk){
    (void)e;(void)c;(void)tk;
    if(!ok(s)||!g_r[s]) return amt>0?amt:0; g_ops++;
    if(g_v[s]>0) return 0;
    if(amt>g_c[s]) return g_c[s];
    return amt>0?amt:0;}
JNIEXPORT void JNICALL Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_Vq_a(JNIEnvP e,jclassP c,jint s,jlong amt,jint v){
    (void)e;(void)c; if(!ok(s)||!g_r[s])return; g_ops++;
    g_h[s]-=amt; if(g_h[s]<0)g_h[s]=0; g_v[s]=v;}
JNIEXPORT jlong JNICALL Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_Vq_h(JNIEnvP e,jclassP c,jint s){
    (void)e;(void)c; if(!ok(s)||!g_r[s]) return -1; g_ops++; return (jlong)g_h[s];}
