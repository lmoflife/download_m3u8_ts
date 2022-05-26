package org.example;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.http.Header;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.URI;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;
import java.util.*;

/**
 * desc:
 * author: LiangMeng
 * date: 2022-05-25 09:29
 */
public class WebApplication {
    static {
        Security.addProvider(new BouncyCastleProvider());
    }
    static CloseableHttpClient client = HttpClients.createDefault();
    static CloseableHttpResponse response;
    static CookieStore cookieStore = new BasicCookieStore();
    static HttpGet httpGet = new HttpGet();
    static HttpPost httpPost = new HttpPost();
    static String client_ucToken = "";
    static String[] keyByteStrs = null;
    static byte[] k1 = null;
    static byte[] k2 = null;
    static byte[] k3 = null;
    static String dir = "D://执业药师下载" + File.separator ;
    static String cwareName = "";
    static List<String> loadFiles = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        //Certificate for ip/域名 doesn't match any of the subject altinative names: [] 问题处理
        SSLConnectionSocketFactory scsf = new SSLConnectionSocketFactory(SSLContexts.custom().loadTrustMaterial(null, new TrustSelfSignedStrategy()).build(), NoopHostnameVerifier.INSTANCE);
        client = HttpClients.custom().setSSLSocketFactory(scsf).setDefaultCookieStore(cookieStore).build();

        loadFiles = getloadFiles();

        String[] cwareID_s = {
                "722025","722053","722033","722030",
                "722074","722126","722070","722102",
                "722122","722066","722062","722098",
                "722041","722037","722106","722082"
        };

        for (String cwareID : cwareID_s){
            List<Map<String,Object>> kcmlList = getKcml(cwareID);
            int co = 0;
            for(Map<String,Object> map : kcmlList){
                String zj_name = (String) map.get("name");//章节名称
                List<Map<String,Object>> nodes = (List<Map<String, Object>>) map.get("nodes");
                int load_count = 0;
                for (Map<String,Object> node : nodes) {
                    String nodeName = (String) node.get("name");//视频名称
                    String videoID = (String) node.get("videoID");//视频id

                    File file = new File(dir + cwareName + File.separator+ zj_name + File.separator + nodeName);
                    if (!file.exists())file.mkdirs();
                    if (!loadFiles.contains(file.getPath())) {
                        List<Map<String, String>> tsList = parseM3u8(getM3u8Url(cwareID, videoID));
                        System.err.println("开始下载--> " + file.getPath() + "，ts数量："+ tsList.size());
                        download_ts(tsList, file.getPath(), nodeName);
                    }
                    else System.err.println("跳过已下载："+file.getPath());
                    load_count ++;
                }
                if (nodes.size() == load_count){
                    co ++;
                    System.err.println(zj_name + " 下载完毕，剩余："+(kcmlList.size()-co)+" 章");
                    System.err.println();
                }
            }
        }

    }

    public static void login() throws Exception {

        httpGet.setURI(new URI("https://www.med66.com/"));
        response = client.execute(httpGet);

        httpGet.setURI(new URI("https://member.med66.com/ucenter/loginRegister/getUcToken?jsonpCallback=jsonp"+new Date().getTime()+"&siteType=med66&_="+new Date().getTime()));
        response = client.execute(httpGet);

        Header[] headers = response.getAllHeaders();
        for (Header header : headers){
            if (header.getName().equals("Set-Cookie") && header.getValue().contains("client_ucToken")) {
                String[] strs = header.getValue().split("; ");
                client_ucToken = strs[0].split("=")[1];
            }
        }

        httpPost.setURI(new URI("https://member.med66.com/ucenter/loginRegister/userLogin/password/slider/"+
                client_ucToken+"?&ifwxbind=1&jsonpCallback=jsonp"+new Date().getTime()+
                "&userName=88888888888&passWord=8888888888&appRegUrl=https%3A%2F%2Fwww.med66.com%2F&randCode=&photoRandCodeSign=&verifyId=&_="+new Date().getTime()));
        response = client.execute(httpPost);

        System.err.println("登录成功。。。");
    }

    public static List<Map<String,Object>> getKcml(String cwareID) {
        List<Map<String,Object>> mapList = new ArrayList<>();
        try {
            login();

            httpGet.setURI(new URI("https://elearning.med66.com/xcware/video/videoList/videoList.shtm?cwareID="+cwareID));
            response = client.execute(httpGet);
            String body = EntityUtils.toString(response.getEntity(), "UTF-8");

            Document doc = Jsoup.parse(body);

            Elements elements_title = doc.select(".catalogPages .fl .tit");
            cwareName = elements_title.get(0).text();

            Elements elements = doc.select(".catalog2 .level1");
            for (Element element : elements) {
                Map<String,Object> map = new HashMap<>();
                map.put("state",element.attr("data-state"));
                map.put("name",element.text());
                mapList.add(map);
            }

            List<Map<String,Object>> nodeList = new ArrayList<>();
            elements = doc.select(".catalog2 .pr");
            for (Element element : elements) {
                Map<String,Object> map = new HashMap<>();
                String da = element.attr("data-list");
                String videoID = element.attr("id");
                Elements elements1 = element.select(".fl");
                String name = elements1.get(1).attr("title");
                name = name.replace("　","-");

                map.put("da",da);
                map.put("videoID",videoID);
                map.put("name",name);
                nodeList.add(map);
            }

            for(Map<String,Object> emp : mapList){
                String state = (String) emp.get("state");
                List<Map<String,Object>> nodes = new ArrayList<>();
                for(Map<String,Object> node : nodeList){
                    String da = (String) node.get("da");
                    if (da.startsWith(state+"#"))nodes.add(node);
                }
                emp.put("nodes",nodes);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        System.err.println(cwareName + " " + mapList.size()+"章");
        return mapList;
    }

    public static String getM3u8Url(String cwareID, String videoID) {
        String url = null;
        try {
            //login();

            httpGet.setURI(new URI("https://elearning.med66.com/xcware/video/h5video/videoPlay.shtm?cwareID="+cwareID+"&videoID="+videoID+"&startTime=0"));
            response = client.execute(httpGet);
            String body = EntityUtils.toString(response.getEntity(), "UTF-8");

            String us = body.substring(body.indexOf("JSON.parse("),body.indexOf("window.cdelmedia.vCookiePath"));
            us = us.replace("\\","");
            us = us.substring(us.indexOf("{"),us.lastIndexOf("}")+1);

            JSONObject jsonObject = JSONObject.parseObject(us);
            url = "https:"+jsonObject.getString("videoPath");
        }catch (Exception e){
            e.printStackTrace();
            System.err.println("获取m3u8地址失败。。。。");
        }
        //if (url != null) System.err.println("获取m3u8地址成功");
        return url;
    }

    public static List<Map<String,String>> parseM3u8(String m3u8Url) throws Exception {//解析M3u8
        List<Map<String,String>> tsList = new ArrayList<>();

        httpGet.setURI(new URI(m3u8Url));
        response = client.execute(httpGet);
        String body = EntityUtils.toString(response.getEntity(), "UTF-8");
        String keyStr = "";

        String[] split = body.split("\\n");
        for (String s : split) {
            if (s.contains("EXT-X-KEY")) {
                String[] split1 = s.split(",");
                for (String s1 : split1) {
                    if (s1.contains("URI")) {
                        String uu = s1.split("=", 2)[1];
                        uu = uu.replace("\"","");
                        httpGet.setURI(new URI(uu));
                        response = client.execute(httpGet);
                        keyStr = EntityUtils.toString(response.getEntity(), "UTF-8");
                        continue;
                    }
                }
            }
        }

        String keyByteStr = new String(java.util.Base64.getDecoder().decode(keyStr.getBytes()));
        keyByteStr = keyByteStr.replace("|&|",",");
        keyByteStrs = keyByteStr.split(",");

        k1 = java.util.Base64.getDecoder().decode(keyByteStrs[0]);
        k2 = java.util.Base64.getDecoder().decode(keyByteStrs[1]);
        k3 = java.util.Base64.getDecoder().decode(keyByteStrs[2]);

        for (String s : split) {
            if (s.contains("ssec.chinaacc.com")) {
                Map<String,String> map = new HashMap<>();
                String eStr = s.substring(s.indexOf(".mp4/"),s.indexOf("?"));
                String fileName = eStr.substring(5,eStr.length());

                map.put("name",fileName);
                map.put("url","https:/"+s);
                tsList.add(map);
            }
        }
        //System.err.println("获取到key： "+keyByteStr);

        return tsList;
    }

    public static void download_ts(List<Map<String,String>> tsList, String path,String nodeName) {
        try {
            Set<File> finishedFiles = new LinkedHashSet<>();
            for (Map<String,String> ts : tsList){
                String ts_name = ts.get("name");
                String ts_url = ts.get("url");
                httpGet = new HttpGet(ts_url);
                response = client.execute(httpGet);

                byte[] fileByte = EntityUtils.toByteArray(response.getEntity());

                Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
                AlgorithmParameterSpec paramSpec = new IvParameterSpec(k1);
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(k2, "AES-CBC"), paramSpec);
                byte[] decrypt = cipher.doFinal(fileByte);

                if (decrypt != null){
                    File file1 = new File(path + File.separator + ts_name);
                    FileOutputStream outputStream1 = new FileOutputStream(file1);
                    outputStream1.write(decrypt);
                    finishedFiles.add(file1);
                    outputStream1.close();
                    //System.err.println("解密ts文件："+ts_name);
                }
                //Thread.sleep(3000);
            }

            File tsFile = new File(path+File.separator + nodeName +".ts");
            FileOutputStream fileOutputStream = new FileOutputStream(tsFile);
            byte[] b = new byte[4096];
            for (File f : finishedFiles) {
                FileInputStream fileInputStream = new FileInputStream(f);
                int len;
                while ((len = fileInputStream.read(b)) != -1) fileOutputStream.write(b, 0, len);
                fileInputStream.close();
                fileOutputStream.flush();
                f.delete();//删除分段ts
            }
            fileOutputStream.close();
            keyByteStrs = null;
            loadFiles.add(path);
            addloadFiles();
        }catch (Exception e){
            System.err.println("下载失败--> "+path+"\\"+nodeName);
        }

    }

    public static List<String> getloadFiles() throws Exception {
        File files = new File(dir);
        if (!files.exists())files.mkdirs();

        List<String> list = new ArrayList<>();
        File file = new File(dir+"已下载文件记录.txt");
        if (file.exists()){
            StringBuilder result = new StringBuilder();
            try{
                BufferedReader br = new BufferedReader(new FileReader(file));//构造一个BufferedReader类来读取文件
                String s = null;
                while((s = br.readLine())!=null)
                    result.append(System.lineSeparator()+s);
                br.close();
            }catch(Exception e){
                e.printStackTrace();
            }
            list = JSONArray.parseArray(result.toString());
        }else{
            file.createNewFile();
        }
        return list;
    }

    public static void addloadFiles() throws Exception {
        File file = new File(dir+"已下载文件记录.txt");
        PrintStream printStream = new PrintStream(new FileOutputStream(file));
        printStream.print(JSONObject.toJSONString(loadFiles));
    }

}
