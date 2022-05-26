# download_m3u8_ts
# java下载m3u8视频，解密并合并ts

## 背景
#### 因为老婆在教育网站上购买的网课不能下载，只能登录网站在线播放，并且是限期的，于是就有了该项目。

## 流程
### 使用 org.apache.httpcomponents.httpclient
### 用账号密码模拟登录
### 获取课程列表信息
### 根据课程信息获取m3u8地址
### 解析m3u8文件 获得key地址和ts链接集合
### 获得key内容 并Base64.decode(key) 得到解密key 和偏移量（IV）
### 依次下载ts文件并解密
### 合并ts



