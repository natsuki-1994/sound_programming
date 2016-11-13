# coding:utf-8

# import time
import wave
import numpy
import scipy.fftpack
from scipy.io.wavfile import write
import matplotlib.pyplot
from pylab import *


def print_wave_information():
    print("Channel num : {} channel".format(wf.getnchannels()))
    print("Sample size : {} byte {} bit".format(wf.getsampwidth(), wf.getsampwidth() * 8))
    print("Sampling rate : {} Hz".format(fs))
    print("Frame num : {}".format(n_len))
    print("Params : {}".format(wf.getparams()))
    print("Sec : {} sec".format(float(n_len / fs)))


# FFT -> noise cancel -> IFFT
def noise_canceling():
    n_fft = 128
    n_overlap = 2
    n_shift = n_fft / n_overlap

    window = numpy.hanning(n_fft)  # 窓関数
    threshold = 0.5  # 雑音除去のしきい値

    for start in range(0, n_len - n_fft, n_shift):
        xs_cut = xs[start: start + n_fft]
        xs_win = xs_cut * window
        Xs = scipy.fftpack.fft(xs_win, n_fft)

        # 周波数振幅と周波数位相
        Xs_amplitude_spectrum = numpy.array([numpy.sqrt(c.real ** 2 + c.imag ** 2) for c in Xs])
        Xs_phase_spectrum = numpy.array([numpy.arctan2(c.imag, c.real) for c in Xs])

        # 信号処理
        Xs_amplitude_spectrum -= threshold  # 周波数振幅からしきい値を減算
        Xs_amplitude_spectrum[Xs_amplitude_spectrum < 0.0] = 0.0  # 周波数振幅が負の値を0に修正

        Z_temp = Xs_amplitude_spectrum * numpy.cos(Xs_phase_spectrum) + Xs_amplitude_spectrum * numpy.sin(Xs_phase_spectrum) * 1j
        z_temp = scipy.fftpack.ifft(Z_temp, n_fft)

        ys[start: start + n_fft] += numpy.real(z_temp)


def stretch_voice():
    offset0 = 0
    offset1 = 0
    template_size = 441  # fs * 0.01
    p_min = 220  # fs * 0.005
    p_max = 882  # fs * 0.02

    while offset0 + p_max * 2 < n_len:
        al = ys[offset0: offset0 + template_size]
        r_max = 0.0
        p = p_min
        for tau in range(p_min, p_max):
            bl = ys[offset0 + tau: offset0 + tau + template_size]  # tauずらしたもの
            r = numpy.dot(al, bl)
            if r > r_max:
                r_max = r  # 相関関数のピーク値
                p = tau  # 音データの基本周期

        print("相関関数のピーク値\t{}\t".format(r_max))
        print("音データの基本周期\t{}".format(p))
        ws[offset1: offset1 + p] = ys[offset0: offset0 + p]

        temp1 = numpy.arange(p, dtype="float32") / p
        temp2 = numpy.ones(p) - temp1
        ws[offset1 + p: offset1 + 2 * p] = ys[offset0: offset0 + p] * temp1
        ws[offset1 + p: offset1 + 2 * p] += ys[offset0 + p: offset0 + 2 * p] * temp2

        ws[offset1 + 2 * p: offset1 + 3 * p] = ys[offset0 + p: offset0 + 2 * p]

        offset0 += 2 * p
        offset1 += 3 * p


# ノイズキャンセルの結果をグラフ表示
def plot_result_noise_canceling():
    fig = matplotlib.pyplot.figure(1, figsize=(8, 10))
    ax = fig.add_subplot(211)
    ax.plot(xs[: fs * 10])
    ax.set_title("input signal")
    ax.set_xlabel("time [pt]")
    ax.set_ylabel("amplitude")

    ax = fig.add_subplot(212)
    ax.plot(ys[: fs * 10])
    ax.set_title("output signal")
    ax.set_xlabel("time [pt]")
    ax.set_ylabel("amplitude")

    matplotlib.pyplot.show()


if __name__ == "__main__":

    wf = wave.open("sample10.wav", "r")

    # 入出力ファイル長
    fs = wf.getframerate()  # ファイルのサンプリング周波数
    n_len = wf.getnframes()  # 入力ファイル長
    n_len_stretch = int(n_len * 1.5) + 1  # 出力ファイルは入力ファイルの1.5倍

    # noise canceling 入出力バッファ
    xs = wf.readframes(n_len)
    xs = frombuffer(xs, dtype="int16") / 32768.0  # -1から+1に正規化
    ys = numpy.zeros(n_len)

    # stretch voice 出力バッファ
    ws = numpy.zeros(n_len_stretch)

    print_wave_information()
    noise_canceling()
    stretch_voice()
    plot_result_noise_canceling()

    # 音楽ファイル書き込み
    write("sample10_nc_stretch.wav", fs, ws)
