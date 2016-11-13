# coding:utf-8

import time
import wave
import numpy
import scipy.fftpack
import matplotlib.pyplot
from pylab import *

if __name__ == "__main__":

    wf = wave.open("sin.wav", "r")

    n_len = wf.getnframes()
    n_fft = 128
    n_overlap = 2
    n_shift = n_fft / n_overlap
    fs = wf.getframerate()  # サンプリング周波数

    # 入力バッファ
    xs = wf.readframes(n_len)
    xs = frombuffer(xs, dtype="int16") / 32768.0  # -1から+1に正規化

    # 中間バッファ
    zs = numpy.zeros(n_len)
    Zs = numpy.zeros(n_fft)

    # 出力バッファ
    ys = numpy.zeros(n_len)

    # 窓関数
    window = numpy.hanning(n_fft)

    # 雑音除去のしきい値
    threshold = 0.5

    # FFT -> IFFT
    start_time = time.time()
    for start in range(0, n_len - n_fft, n_shift):
        xs_cut = xs[start: start + n_fft]
        xs_win = xs_cut * window
        Xs = scipy.fftpack.fft(xs_win, n_fft)

        # 周波数振幅のみ考慮 / 周波数位相は無視する
        Xs_amplitude_spectrum = numpy.array([numpy.sqrt(c.real ** 2 + c.imag ** 2) for c in Xs])
        freq_list = scipy.fftpack.fftfreq(n_fft, d=1.0/fs)
        print Xs_amplitude_spectrum

        # 信号処理
        Xs_amplitude_spectrum -= threshold
        print Xs_amplitude_spectrum
        Xs_amplitude_spectrum[Xs_amplitude_spectrum < 0.0] = 0.0
        print Xs_amplitude_spectrum
        exit(-1)
        zs = scipy.fftpack.ifft(Zs, n_fft)

        ys[start: start + n_fft] += numpy.real(zs)
    needed_time = time.time() - start_time
    print("needed time for fft -> ifft (5s data):{}[sec]".format(needed_time))

    # 3秒分プロット
    fig = matplotlib.pyplot.figure(1, figsize=(8, 10))
    ax = fig.add_subplot(211)
    ax.plot(xs[: fs / 10])
    ax.set_title("input signal")
    ax.set_xlabel("time [pt]")
    ax.set_ylabel("amplitude")

    ax = fig.add_subplot(212)
    ax.plot(ys[: fs / 10])
    ax.set_title("output signal")
    ax.set_xlabel("time [pt]")
    ax.set_ylabel("amplitude")

    matplotlib.pyplot.show()
