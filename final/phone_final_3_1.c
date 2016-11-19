/*
  無音区間無送信 + 雑音除去 + 音の伸張
  TCP + マルチスレッド
*/
#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <sys/socket.h>  //ip, tcp, socket, listen, bind, accept
                         //send, recv, shutdown
#include <netinet/in.h>  //ip, tcp, inet_addr
#include <netinet/ip.h>  //ip
#include <netinet/tcp.h> //tcp
#include <sys/types.h>   //socket, bind, listen, accept, send, recv
#include <errno.h>       //perror
#include <stdlib.h>      //exit, atoi
#include <arpa/inet.h>   //inet_addr, htons
#include <pthread.h>     //pthread
#include <unistd.h>      //close

#define SAMPLING_FREQ   44100 /* 標本化周波数 */
#define DATA_SIZE       44100 /* short*44100(= 44100*2 bits)読み取り */
#define DATA_SIZE_BIG   66819 /* (int)(DATA_SIZE / rate) + 1 */
#define TEMPLATE_SIZE   441   /* (int)(pcm1.fs * 0.01) */
#define PMIN            220   /* (int)(pcm1.fs * 0.005) */
#define PMAX            882   /* (int)(pcm1.fs * 0.02) */
#define PMAX_PLUS_ONE   883   /* (int)(pcm1.fs * 0.02) + 1 */         

typedef struct {
  int fs;     /* 標本化周波数 */
  int bits;   /* 量子化ビット数 */
  int length; /* 音データの長さ */
  double *s;  /* 音データ */
} MONO_PCM;

double threshold = 1.0; /* 雑音除去用しきい値 */
int count = 0;          /* 無音区間無送信用 */
int flag  = 0;          /* 0 送信 : 1 無送信 */
double rate = 0.66;     /* 音の伸張用 */

int shutdown_flag = 0;  /* shutdown用フラグ */

/* 無音区間無送信用 ok */
double x_real[DATA_SIZE];
double x_imag[DATA_SIZE];
double A[DATA_SIZE];
double T[DATA_SIZE];

/* 音の伸張用 ok */
double x[TEMPLATE_SIZE];
double y[TEMPLATE_SIZE];
double r[PMAX_PLUS_ONE];

/* 雑音除去用 ok */
double x_real_noise[DATA_SIZE_BIG];
double x_imag_noise[DATA_SIZE_BIG];
double A_noise[DATA_SIZE_BIG];
double T_noise[DATA_SIZE_BIG];
double y_real_noise[DATA_SIZE_BIG];
double y_imag_noise[DATA_SIZE_BIG];

/* 引数(MONO_PCM構造体, 入力音データ(short*DATA_SIZE) */
void mono_wave_read(MONO_PCM *pcm, short in_data[]) {
  int n;
  short data;
  
  pcm->fs     = SAMPLING_FREQ; /* 標本化周波数 */
  pcm->bits   = 16;            /* 量子化ビット数 */
  pcm->length = DATA_SIZE;     /* 構造体 音データの長さ(short) */
  pcm->s      = calloc(pcm->length, sizeof(double)); /* メモリの確保 */
  
  for (n = 0; n < pcm->length; n++) {
    data      = in_data[n];             /* 音データの読み取り */
    pcm->s[n] = (double)data / 32768.0; /* 音データを-1以上1未満の範囲に正規化(16bit) */
  }
}

/* 引数(MONO_PCM構造体, 出力音データ(short*DATA_SIZE) */
void mono_wave_write(MONO_PCM *pcm, short out_data[]) {
  int n;
  short data;
  double s;
  
  for (n = 0; n < pcm->length; n++) {
    s = (pcm->s[n] + 1.0) / 2.0 * 65536.0;
    /* オーバーフローした時 */
    if (s > 65535.0) {
      s = 65535.0;
    } else if (s < 0.0) {
      s = 0.0;
    }
    data = (short)(s + 0.5) - 32768; /* 四捨五入とオフセットの調節 */
    out_data[n] = data;              /* 音データの書き出し */
  }
}

/* y = log2(x) */
int log_2(int x) {
  int y;
  y = (int)log2(x);

  return y;
}

/* y = 2 ^ x */
int pow_2(int x) {
  int y;
  if (x == 0) {
    y = 1;
  } else {
    y = (int)pow(2.0, x);
  }
  
  return y;
}

/* 高速フーリエ変換 */
void FFT(double x_real[], double x_imag[], int N) {
  int i, j, k, n, m, r, stage, number_of_stage, *index;
  double a_real, a_imag, b_real, b_imag, c_real, c_imag, real, imag;
  
  /* FFTの段数 */
  number_of_stage = log_2(N);

  /* バタフライ計算 */
  for (stage = 1; stage <= number_of_stage; stage++) {
    for (i = 0; i < pow_2(stage - 1); i++) {
      for (j = 0; j < pow_2(number_of_stage - stage); j++) {
	n      = pow_2(number_of_stage - stage + 1) * i + j;
	m      = pow_2(number_of_stage - stage) + n;
	r      = pow_2(stage - 1) * j;
        a_real = x_real[n];
        a_imag = x_imag[n];
        b_real = x_real[m];
        b_imag = x_imag[m];
        c_real =  cos((2.0 * M_PI * r) / N);
        c_imag = -sin((2.0 * M_PI * r) / N);
	if (stage < number_of_stage) {
	  x_real[n] = a_real + b_real;
          x_imag[n] = a_imag + b_imag;
          x_real[m] = (a_real - b_real) * c_real - (a_imag - b_imag) * c_imag;
          x_imag[m] = (a_imag - b_imag) * c_real + (a_real - b_real) * c_imag;
        } else {
	  x_real[n] = a_real + b_real;
	  x_imag[n] = a_imag + b_imag;
	  x_real[m] = a_real - b_real;
	  x_imag[m] = a_imag - b_imag;
	}
      }
    }
  } /* バタフライ計算 end */
  
  /* インデックスの並び替えのためのテーブルの作成 */
  index = calloc(N, sizeof(int));
  for (stage = 1; stage <= number_of_stage; stage++) {
    for (i = 0; i < pow_2(stage - 1); i++) {
      index[pow_2(stage - 1) + i] = index[i] + pow_2(number_of_stage - stage);
    }
  }
  
  /* インデックスの並び替え */
  for (k = 0; k < N; k++) {
    if (index[k] > k) {
      real             = x_real[index[k]];
      imag             = x_imag[index[k]];
      x_real[index[k]] = x_real[k];
      x_imag[index[k]] = x_imag[k];
      x_real[k]        = real;
      x_imag[k]        = imag;
    }
  }
  
  free(index);
}

/* 高速フーリエ逆変換 */
void IFFT(double x_real[], double x_imag[], int N) {
  int i, j, k, n, m, r, stage, number_of_stage, *index;
  double a_real, a_imag, b_real, b_imag, c_real, c_imag, real, imag;
  
  /* IFFTの段数 */
  number_of_stage = log_2(N);
  
  /* バタフライ計算 */
  for (stage = 1; stage <= number_of_stage; stage++) {
    for (i = 0; i < pow_2(stage - 1); i++) {
      for (j = 0; j < pow_2(number_of_stage - stage); j++) {
	n      = pow_2(number_of_stage - stage + 1) * i + j;
	m      = pow_2(number_of_stage - stage) + n;
	r      = pow_2(stage - 1) * j;
	a_real = x_real[n];
	a_imag = x_imag[n];
	b_real = x_real[m];
	b_imag = x_imag[m];
	c_real = cos((2.0 * M_PI * r) / N);
	c_imag = sin((2.0 * M_PI * r) / N);
	if (stage < number_of_stage) {
	  x_real[n] = a_real + b_real;
	  x_imag[n] = a_imag + b_imag;
	  x_real[m] = (a_real - b_real) * c_real - (a_imag - b_imag) * c_imag;
	  x_imag[m] = (a_imag - b_imag) * c_real + (a_real - b_real) * c_imag;
	} else {
	  x_real[n] = a_real + b_real;
	  x_imag[n] = a_imag + b_imag;
	  x_real[m] = a_real - b_real;
	  x_imag[m] = a_imag - b_imag;
	}
      }
    }
  } /* バタフライ計算 end */
  
  /* インデックスの並び替えのためのテーブルの作成 */
  index = calloc(N, sizeof(int));
  for (stage = 1; stage <= number_of_stage; stage++) {
    for (i = 0; i < pow_2(stage - 1); i++) {
      index[pow_2(stage - 1) + i] = index[i] + pow_2(number_of_stage - stage);
    }
  }
  
  /* インデックスの並び替え */
  for (k = 0; k < N; k++) {
    if (index[k] > k) {
      real             = x_real[index[k]];
      imag             = x_imag[index[k]];
      x_real[index[k]] = x_real[k];
      x_imag[index[k]] = x_imag[k];
      x_real[k]        = real;
      x_imag[k]        = imag;
    }
  }
  
  /* 計算結果をNで割る */
  for (k = 0; k < N; k++) {
    x_real[k] /= N;
    x_imag[k] /= N;
  }
  
  free(index);
}

void sound_processing_src(short processing_data[]) {
  
  /* --------------------無音判定-------------------- */

  MONO_PCM pcm0;
  int n;

  count = 0; /* 無音除去用カウンタリセット */
  flag  = 0; /* 無音除去用フラグリセット */ 
  
  mono_wave_read(&pcm0, processing_data); /* 入力音データをpcm0に移す */

  /* x(n) FFT */
  for (n = 0; n < DATA_SIZE; n++) {
    x_real[n] = pcm0.s[n];
    x_imag[n] = 0.0;
  }
  FFT(x_real, x_imag, DATA_SIZE);         /* 結果は上書きされる */
  
  /* 振幅スペクトルA[n]と位相スペクトルT[n] */
  for (n = 0; n < DATA_SIZE; n++) {
    A[n] = sqrt(x_real[n] * x_real[n] + x_imag[n] * x_imag[n]);
    if (x_imag[n] != 0.0 && x_real[n] != 0.0) {
      T[n] = atan2(x_imag[n], x_real[n]);
    }
  }
  
  /* 
     振幅スペクトル - (しきい値 * 5)
     無音判定(マイク近くの声のみ拾いたい)                     
  */
  for (n = 0; n < DATA_SIZE; n++) {
    A[n] -= (threshold * 8);
    if (A[n] < 0.0) {
      A[n] = 0.0;
      count++;
    }
  }
  if (count > (int)DATA_SIZE * 0.98) {
    flag = 1;
  }

  free(pcm0.s);
}

void sound_processing_dest(short processing_data[], short processed_data[]) {
  
  MONO_PCM pcm0, pcm1, pcm2;
  
  mono_wave_read(&pcm0, processing_data); /* 入力音データをpcm0に移す */
  
  pcm1.fs     = pcm0.fs;
  pcm1.bits   = pcm0.bits;
  pcm1.length = DATA_SIZE_BIG;
  pcm1.s      = calloc(pcm1.length, sizeof(double));
  pcm2.fs     = pcm0.fs;
  pcm2.bits   = pcm0.bits;
  pcm2.length = DATA_SIZE_BIG;
  pcm2.s      = calloc(pcm2.length, sizeof(double));

  /* --------------------音の伸張-------------------- */

  int n, m, pmin, pmax, p, q, offset0, offset1;
  double max_of_r;

  offset0 = 0;
  offset1 = 0;

  while (offset0 + PMAX * 2 < pcm0.length) {
    for (n = 0; n < TEMPLATE_SIZE; n++) {
      x[n] = pcm0.s[offset0 + n];       /* 本来の音データ */
    }

    max_of_r = 0.0;
    p = PMIN;
    for (m = PMIN; m <= PMAX; m++) {
      for (n = 0; n < TEMPLATE_SIZE; n++) {
	y[n] = pcm0.s[offset0 + m + n]; /* mサンプルずらしたデータ */
      }
      r[m] = 0.0;
      for (n = 0; n < TEMPLATE_SIZE; n++) {
	r[m] += x[n] * y[n]; /* 相関関数 */
      }
      if (r[m] > max_of_r) {
	max_of_r = r[m];     /* 相関関数のピーク */
	p = m;               /* 音データの基本周期 */
      }
    }

    for (n = 0; n < p; n++) {
      pcm1.s[offset1 + n] = pcm0.s[offset0 + n];
    }
    for (n = 0; n < p; n++) {
      pcm1.s[offset1 + p + n]  = pcm0.s[offset0 + p + n] * (p - n) / p;
      pcm1.s[offset1 + p + n] += pcm0.s[offset0 + n] * n / p;
    }

    q = (int)(p * rate / (1.0 - rate) + 0.5);
    for (n = p; n < q; n++) {
      if (offset0 + n >= pcm0.length) {
	break;
      }
      pcm1.s[offset1 + p + n] = pcm0.s[offset0 + n];
    }

    offset0 += q;
    offset1 += p + q;
  }

  /* --------------------雑音除去-------------------- */

  /* x(n) FFT */
  for (n = 0; n < DATA_SIZE_BIG; n++) {
    x_real_noise[n] = pcm1.s[n];
    x_imag_noise[n] = 0.0;
  }
  FFT(x_real_noise, x_imag_noise, DATA_SIZE_BIG); /* 結果は上書きされる */
  
  /* 振幅スペクトルと位相スペクトル */
  for (n = 0; n < DATA_SIZE_BIG; n++) {
    A_noise[n] = sqrt(x_real_noise[n] * x_real_noise[n] + x_imag_noise[n] * x_imag_noise[n]);
    if (x_imag_noise[n] != 0.0 && x_real_noise[n] != 0.0) {
      T_noise[n] = atan2(x_imag_noise[n], x_real_noise[n]);
    }
  }
  
  /* 振幅スペクトル - しきい値 */
  for (n = 0; n < DATA_SIZE_BIG; n++) {
    A_noise[n] -= threshold;
    if (A_noise[n] < 0.0) {
      A_noise[n] = 0.0;
    }
  }
  
  for (n = 0; n < DATA_SIZE_BIG; n++) {
    y_real_noise[n] = A_noise[n] * cos(T_noise[n]);
    y_imag_noise[n] = A_noise[n] * sin(T_noise[n]);
  }
  
  /* 高速フーリエ逆変換 */
  IFFT(y_real_noise, y_imag_noise, DATA_SIZE_BIG);
  
  /* 結果をpcm1に格納 */
  for (n = 0; n < DATA_SIZE_BIG; n++) {
    pcm2.s[n] = y_real_noise[n];
  }

  mono_wave_write(&pcm2, processed_data); /* pcm2を出力音データに移す */

  free(pcm0.s);
  free(pcm1.s);
  free(pcm2.s);
}

/* perror関数 */
int error(char *message) {
  perror(message);
  exit(1);
}

static void *server_client_send(void *arg_s) {

  int x;
  int s    = *(int *)arg_s;
  FILE *fp = popen("rec -t raw -b 16 -c 1 -e s -r 44100 -", "r");
  if (fp == NULL) error("popen(client_send");

  short data_in_src[DATA_SIZE];

  while (1) {
    for (x = 0; x < DATA_SIZE; x++) {
      data_in_src[x] = 0;
    }
    int m = fread(data_in_src, sizeof(short), DATA_SIZE, fp);
    if (m == -1) error("fread(client_send)");
    if (m ==  0 || shutdown_flag == 1) {
      break;
    }
    sound_processing_src(data_in_src);
    if (flag == 1) continue;
    int n = send(s, data_in_src, m * sizeof(short), 0);
    if (n == -1) error("send(client_send)");
  }

  fclose(fp);

  return NULL;
}

static void *server_client_recv(void *arg_s) {

  int x;
  int s    = *(int *)arg_s;
  FILE *wp = popen("play -t raw -b 16 -c 1 -e s -r 44100 -", "w");
  if (wp == NULL) error("popen(server_client_recv)");

  short data_in_dest[DATA_SIZE];
  short data_out_dest[DATA_SIZE_BIG];

  while (1) {
    for (x = 0; x < DATA_SIZE; x++) {
      data_in_dest[x] = 0;
    }
    for (x = 0; x < DATA_SIZE_BIG; x++) {
      data_out_dest[x] = 0;
    }
    int m = recv(s, data_in_dest, DATA_SIZE * sizeof(short), 0);
    if (m == -1) error("recv(server_client_recv)");
    if (m == 0) {
      shutdown_flag = 1;
      break;
    }
    sound_processing_dest(data_in_dest, data_out_dest);
    /* 3000 引くのは音がプツプツ途切れないため */
    int n = fwrite(data_out_dest, sizeof(short), DATA_SIZE_BIG - 3000, wp);
    if (n == -1) error("fwrite(server_client_recv)");
  }

  fclose(wp);

  return NULL;
}

int server(int port) {

  /* socket */
  int ss = socket(PF_INET, SOCK_STREAM, 0);
  if (ss == -1) error("socket(server)");

  /* bind */
  struct sockaddr_in addr;
  addr.sin_family      = AF_INET;
  addr.sin_port        = htons(port);
  addr.sin_addr.s_addr = INADDR_ANY;  //どのアドレスでも待ち受け         

  int bnd = bind(ss, (struct sockaddr *)&addr, sizeof(addr));
  if (bnd == -1) error("bind");

  /* listen */
  int lsn = listen(ss, 10);
  if (lsn == -1) error("listen");

  /* accept */
  struct sockaddr_in client_addr;
  socklen_t len = sizeof(struct sockaddr_in);
  int s         = accept(ss, (struct sockaddr *)&client_addr, &len);
  if (s == -1) error("accept");
  
  /* マルチスレッド */
  pthread_t th1, th2;

  pthread_create(&th1, NULL, server_client_recv, (void *)&s);
  pthread_create(&th2, NULL, server_client_send, (void *)&s);

  pthread_join(th1, NULL);
  pthread_join(th2, NULL);

  int l = close(s);
  if (l == -1) error("close");

  if (shutdown_flag == 1)  printf("\n\n\n*----------------------------*\n         See You !!!\n*----------------------------*\n");
  
  return 0;
}

int client(char *server_address, int port) {

  /* socket */
  int s = socket(PF_INET, SOCK_STREAM, 0);
  if (s == -1) error("socket");

  /* connect */
  struct sockaddr_in addr;
  addr.sin_family      = AF_INET;
  addr.sin_addr.s_addr = inet_addr(server_address);
  addr.sin_port        = htons(port);
  int ret = connect(s, (struct sockaddr *)&addr, sizeof(addr));
  if (ret == -1) error("connect");

  /* マルチスレッド */
  pthread_t th1, th2;

  pthread_create(&th1, NULL, server_client_send, (void *)&s);
  pthread_create(&th2, NULL, server_client_recv, (void *)&s);

  pthread_join(th1, NULL);
  pthread_join(th2, NULL);

  /* shutdown */
  int k = shutdown(s, SHUT_WR);
  if (k == -1) error("shutdown");

  if (shutdown_flag == 1)  printf("\n\n\n*----------------------------*\n         See You !!!\n*----------------------------*\n");
  
  return 0;
}

int main(int argc, char **argv) {

  int port;

  if (argc == 2) {
    port = atoi(argv[1]);
    server(port);
  } else if (argc == 3) {
    port = atoi(argv[2]);
    client(argv[1], port);
  }

  return 0;
}
