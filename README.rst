=============
Smartphone DA
=============
Smartphone DA 由數個 Activity 以及 Service 組成

**Note**: 此 DA 內的 DAN 為舊版，和老師討論以後已將架構更改


SelectECActivity
-----------------
SelectECActivity 提供 GUI 讓 User 點選 IoTtalk Server

DAN 會搜尋同一個 LAN 底下的 IoTtalk Server，並顯示在螢幕上

GUI 上另有一個輸入框，可以手動輸入 IP:Port 直接連接

選擇 IoTtalk Server 以後，FeatureActivity 會開啟，SelectECActivity 會結束


FeatureActivity
----------------
FeatureActivity 提供 GUI 讓 User

1.  開啟/關閉手機的各個 Feature（由 SwitchFeatureFragment 負責）
2.  查看 Display（由 ChartFragment 負責）

GUI 設計成兩個 Tab，以 ActionBar 實作


IDFs/ODFs
----------
目前 Smartphone DA 有實作以下 Feature，分別由不同的 Service 或 Class 處理：

* [IDF] Acceleration: AccelerometerService

  - 3 parameters: [float, float, float]，對應到三軸加速度，單位為 ``m/s**2``

* [IDF] Microphone, Raw-mic: MicService, AudioTrackManager, Waveshape

  - 1 parameter: [json]，用來包裝資料，格式為 ``{"data": [int, int, ...]}`` ，每個 ``int`` 的範圍為 0 ~ 255
  
* [ODF] Speaker: SpeakerService

  - 1 parameter: [int]，範圍為 0 ~ 20，在 DA 內對應到各個頻率
  
* [ODF] Display: ChartFragment, LineGraph

  - 1 parameter: [json]，格式較複雜


其他
-----
* Utils: 包含一些工具函式
* Constants: 包含各個常數
