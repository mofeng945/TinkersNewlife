/* TNL853 kernel (obfuscated symbols) */
typedef int       jint;
typedef long long jlong;
typedef void*     JNIEnvP;
typedef void*     jclassP;
#define JNIEXPORT __declspec(dllexport)
#define JNICALL __stdcall
#define MG 0x544E4C31L
#define TK 0x1A2B3C4DL
#define AB 3
#define NS 32
static long long H[NS], M[NS], C[NS];
static int V[NS], R[NS];
static long long O = 0;
static int ok(int s){return s>=0&&s<NS;}
JNIEXPORT jlong JNICALL Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_a_a(JNIEnvP e,jclassP c){(void)e;(void)c;return ((jlong)MG<<8)|(jlong)AB;}
JNIEXPORT jlong JNICALL Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_a_b(JNIEnvP e,jclassP c){(void)e;(void)c;return (jlong)(TK^(O*7919));}
JNIEXPORT jlong JNICALL Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_a_c(JNIEnvP e,jclassP c){long long x=(long long)MG;int i;(void)e;(void)c;for(i=0;i<NS;++i)x=(x*131+H[i]+M[i]+C[i]+V[i]+R[i])&0x7FFFFFFFFFFFFFFFLL;return (jlong)(x^(O*7919));}
JNIEXPORT void JNICALL Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_a_d(JNIEnvP e,jclassP c,jint s,jlong m,jlong p,jint v){(void)e;(void)c;if(!ok(s))return;M[s]=m;H[s]=m;C[s]=p;V[s]=0;R[s]=1;O++;}
JNIEXPORT jlong JNICALL Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_a_e(JNIEnvP e,jclassP c,jint s,jlong n,jint t){long long lo;(void)e;(void)c;(void)t;if(!ok(s)||!R[s])return n;O++;if(V[s]>0)V[s]--;lo=H[s]-C[s];if(lo<0)lo=0;H[s]=n<lo?lo:n;return (jlong)H[s];}
JNIEXPORT jlong JNICALL Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_a_f(JNIEnvP e,jclassP c,jint s,jlong w,jint t){(void)e;(void)c;(void)t;if(!ok(s)||!R[s])return w>0?w:0;O++;if(V[s]>0)return 0;if(w>C[s])return C[s];return w>0?w:0;}
JNIEXPORT void JNICALL Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_a_g(JNIEnvP e,jclassP c,jint s,jlong x,jint v){(void)e;(void)c;if(!ok(s)||!R[s])return;O++;H[s]-=x;if(H[s]<0)H[s]=0;V[s]=v;}
JNIEXPORT jlong JNICALL Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_a_h(JNIEnvP e,jclassP c,jint s){(void)e;(void)c;if(!ok(s)||!R[s])return -1;O++;return (jlong)H[s];}
