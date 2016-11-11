# coding:utf-8

import wave
import numpy as np
from pylab import *


def dft(start_, x_, N_):
    """xのstartサンプル目からNサンプルを周期波形とみなした時のDFT値を求める"""
    X_ = [0.0] * N_
    for k_ in range(N_):
        for n_ in range(N_):
            real_ = np.cos(2 * np.pi * k_ * n_ / N_)
            imag_ = - np.sin(2 * np.pi * k_ * n_ / N_)
            X_[k_] += x_[start_ + n_] * complex(real_, imag_)
    return X_

if __name__ == "__main__":

    wf = wave.open("data/sine.wav", "r")
    fs = wf.getframerate()  # サンプリング周波数
    x = wf.readframes(wf.getnframes())
    x = frombuffer(x, dtype="int16") / 32768.0  # -1から+1に正規化
    wf.close()

    start = 0
    N = 256
    X = dft(start, x, N)
    freqList = [k * fs / N for k in range(N)]  # 周波数のリスト
    amplitudeSpectrum = [np.sqrt(c.real ** 2 + c.imag ** 2) for c in X]
    phaseSpectrum = [np.arctan(int(c.imag), int(c.real)) for c in X]

    # 波形サンプルを描画
    subplot(311)  # 3行1列のグラフの1番目の位置にプロット
    plot(range(start, start+N), x[start:start+N])
    axis([start, start+N, -1.0, 1.0])
    xlabel("time [sample]")
    ylabel("amplitude")

    # 振幅スペクトルを描画
    subplot(312)
    plot(freqList, amplitudeSpectrum, marker='o', linestyle='-')
    axis([0, fs/2, 0, 15])    # ナイキスト周波数まで表示すれば十分
    xlabel("frequency [Hz]")
    ylabel("amplitude spectrum")

    # 位相スペクトルを描画
    subplot(313)
    plot(freqList, phaseSpectrum, marker='o', linestyle='-')
    axis([0, fs/2, -np.pi, np.pi])
    xlabel("frequency [Hz]")
    ylabel("phase spectrum")

    show()