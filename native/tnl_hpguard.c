/* TNL860 kernel: text section self check */
typedef int       jint;
typedef long long jlong;
typedef void*     JNIEnvP;
typedef void*     jclassP;
#define JNIEXPORT __declspec(dllexport)
#define JNICALL __stdcall
#define MG 0x544E4C31L
#define TK 0x1A2B3C4DL
#define AB 5
#define NS 32
extern char __ImageBase;
static long long H[NS], M[NS], C[NS];
static int V[NS], R[NS];
static long long O = 0;
static const unsigned char FP[64] = {0x0B,0x38,0x45,0x62,0xBF,0xEC,0xD9,0x36,0x73,0x10,0x2D,0xFA,0xA7,0x84,0x61,0x4E,0xDB,0x08,0x35,0x52,0x4F,0xBC,0x89,0xE6,0x43,0x60,0x1D,0x2A,0xF7,0xD4,0x91,0x7E,0xAB,0xD8,0xE5,0x02,0x1F,0x4C,0xB9,0x96,0x93,0xB0,0x4D,0x1A,0x07,0xE4,0xC1,0xAE,0x7B,0xA8,0xD5,0xF2,0x2F,0x1C,0x69,0x46,0xE3,0x80,0xBD,0x4A,0x57,0x34,0xF1,0xDE};
#ifndef TCVAL
#define TCVAL 2270635665UL
#endif
static volatile const unsigned long TC = TCVAL;

static unsigned long crc_of(const unsigned char* p, unsigned long n) {
    unsigned long crc = 0xFFFFFFFFUL; unsigned long i; int b;
    for (i = 0; i < n; ++i) {
        crc ^= (unsigned long)p[i];
        for (b = 0; b < 8; ++b) crc = (crc >> 1) ^ (0xEDB88320UL & (unsigned long)(-(long)(crc & 1)));
    }
    return (crc ^ 0xFFFFFFFFUL) & 0xFFFFFFFFUL;
}
static unsigned long text_crc(void) {
    const unsigned char* base = (const unsigned char*)&__ImageBase;
    const unsigned char* nt = base + *(unsigned long*)(base + 0x3C);
    unsigned short nsec = *(unsigned short*)(nt + 6);
    unsigned short optsz = *(unsigned short*)(nt + 20);
    const unsigned char* sec = nt + 24 + optsz;
    unsigned int i;
    for (i = 0; i < nsec; ++i, sec += 40) {
        if (sec[0]=='.'&&sec[1]=='t'&&sec[2]=='e'&&sec[3]=='x'&&sec[4]=='t') {
            unsigned int vs = *(unsigned int*)(sec + 8);
            unsigned int va = *(unsigned int*)(sec + 12);
            return crc_of(base + va, vs);
        }
    }
    return 0;
}
static unsigned long fp_crc(void) {
    unsigned long crc = 0xFFFFFFFFUL; int i, b;
    for (i = 0; i < 64; ++i) {
        crc ^= (unsigned long)FP[i];
        for (b = 0; b < 8; ++b) crc = (crc >> 1) ^ (0xEDB88320UL & (unsigned long)(-(long)(crc & 1)));
    }
    return (crc ^ 0xFFFFFFFFUL) & 0xFFFFFFFFUL;
}
static int ok(int s){return s>=0&&s<NS;}
JNIEXPORT jlong JNICALL Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_a_a(JNIEnvP e,jclassP c){(void)e;(void)c;return ((jlong)MG<<8)|(jlong)AB;}
JNIEXPORT jlong JNICALL Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_a_b(JNIEnvP e,jclassP c){(void)e;(void)c;return (jlong)(TK^(O*7919));}
JNIEXPORT jlong JNICALL Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_a_c(JNIEnvP e,jclassP c){long long x=(long long)MG;int i;(void)e;(void)c;for(i=0;i<NS;++i)x=(x*131+H[i]+M[i]+C[i]+V[i]+R[i])&0x7FFFFFFFFFFFFFFFLL;return (jlong)(x^(O*7919));}
JNIEXPORT jlong JNICALL Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_a_z(JNIEnvP e,jclassP c){(void)e;(void)c;return (jlong)fp_crc();}
JNIEXPORT jlong JNICALL Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_a_y(JNIEnvP e,jclassP c){(void)e;(void)c;return (jlong)text_crc();}
JNIEXPORT jlong JNICALL Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_a_x(JNIEnvP e,jclassP c){(void)e;(void)c;return (jlong)(text_crc()==TC?1L:0L);}
JNIEXPORT void JNICALL Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_a_d(JNIEnvP e,jclassP c,jint s,jlong m,jlong p,jint v){(void)e;(void)c;if(!ok(s))return;M[s]=m;H[s]=m;C[s]=p;V[s]=0;R[s]=1;O++;}
JNIEXPORT jlong JNICALL Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_a_e(JNIEnvP e,jclassP c,jint s,jlong n,jint t){long long lo;(void)e;(void)c;(void)t;if(!ok(s)||!R[s])return n;O++;if(V[s]>0)V[s]--;lo=H[s]-C[s];if(lo<0)lo=0;H[s]=n<lo?lo:n;return (jlong)H[s];}
JNIEXPORT jlong JNICALL Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_a_f(JNIEnvP e,jclassP c,jint s,jlong w,jint t){(void)e;(void)c;(void)t;if(!ok(s)||!R[s])return w>0?w:0;O++;if(V[s]>0)return 0;if(w>C[s])return C[s];return w>0?w:0;}
JNIEXPORT void JNICALL Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_a_g(JNIEnvP e,jclassP c,jint s,jlong x,jint v){(void)e;(void)c;if(!ok(s)||!R[s])return;O++;H[s]-=x;if(H[s]<0)H[s]=0;V[s]=v;}
JNIEXPORT jlong JNICALL Java_com_mofengbaizhi_tinkersnewlife_content_entity_ktulu_a_h(JNIEnvP e,jclassP c,jint s){(void)e;(void)c;if(!ok(s)||!R[s])return -1;O++;return (jlong)H[s];}
