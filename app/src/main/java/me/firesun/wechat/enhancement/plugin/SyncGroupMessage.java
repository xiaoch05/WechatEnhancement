package me.firesun.wechat.enhancement.plugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import me.firesun.wechat.enhancement.PreferencesUtils;
import me.firesun.wechat.enhancement.util.HookParams;

import android.os.Environment;
import java.util.Timer;
import java.util.TimerTask;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.FileInputStream;

import java.io.BufferedReader;


public class SyncGroupMessage implements IPlugin {

    Class GetContactRecordMethodClass;
    public class msgCacheInfo {
        Long msgId;
        int timeescape;
        String from_wxid;
        String from_nickname;
        String groupId;
        String groupNickName;
        String from_aliname;
        String avatar;
        String hdAvatar;
        int sex;
        String province;
        String city;
        String Country;
        String signature;
        String displayRegion;
        int msgType;
        String text;
        long sendTime;
        String img;

        String filePath;

        @Override
        public String toString() {
            return "MSGINFO:" + "<msgID=" + msgId + ">"
                    + "<wxid=" + from_wxid + ">"
                    + "<nickname=" + from_nickname + ">"
                    + "<aliname=" + from_aliname + ">"
                    + "<avatar=" + avatar + ">"
                    + "<hdAvatar=" + hdAvatar + ">"
                    + "<province=" + province + ">"
                    + "<city=" + city + ">"
                    + "<signature=" + signature + ">"
                    + "<msgType=" + msgType + ">"
                    + "<text=" + text + ">"
                    + "<city=" + city + ">"
                    + "<County=" + Country + ">"
                    + "<groupNickName=" + groupNickName + ">";
        }
    }
    private static HashMap<Long, msgCacheInfo> msgCacheMap = new HashMap<Long, msgCacheInfo>();
    Timer timer = new Timer(true);
    String rootPath = Environment.getExternalStorageDirectory().getAbsolutePath();
    TimerTask task = new TimerTask() {
        public void run() {
             {
                try {
                    HashMap<Long, msgCacheInfo> msgCacheTmp;
                    synchronized(msgCacheMap) {
                        msgCacheTmp = (HashMap<Long, msgCacheInfo>)msgCacheMap.clone();
                    }

                    Set<Map.Entry<Long, msgCacheInfo>> entries = msgCacheTmp.entrySet();
                    //for(Map.Entry<Long, msgCacheInfo> entry : entries){
                    //for (Iterator<Map.Entry<Long, msgCacheInfo>> it = entries.iterator(); it.hasNext(); ) {
                    for (Map.Entry<Long, msgCacheInfo> item : entries) {
                        msgCacheInfo info = item.getValue();
                        Long key = item.getKey();

                        if (info.msgType == 3) {
                            if (info.timeescape < 5) {
                                info.timeescape++;
                                continue;
                            } else if (info.timeescape < 30) {
                                info.timeescape++;
                                XposedBridge.log("I should find file, path=" + info.filePath);
                                if (info.filePath != null) {
                                    File file = new File(info.filePath);
                                    if (file != null && file.exists()) {
                                        XposedBridge.log("file has been download finished:" + info.filePath);
                                        synchronized(msgCacheMap) {
                                            msgCacheMap.remove(key);
                                        }
                                        String fileHash = uploadFile("Android" + info.msgId + ".jpg", info.filePath);
                                        if (fileHash != null) {
                                            info.img = fileHash;
                                            httpRequest(info);
                                        }
                                        //file.delete();
                                    }
                                }
                            } else {
                                XposedBridge.log("cannot find image, dropped");
                                synchronized(msgCacheMap) {
                                    msgCacheMap.remove(key);
                                }
                            }
                        } else if (info.msgType == 1) {
                            if (info.timeescape < 3) {
                                info.timeescape++;
                                continue;
                            } else {
                                try {
                                    Object aj = XposedHelpers.callStaticMethod(GetContactRecordMethodClass, "Fw");
                                    Object ad = XposedHelpers.callMethod(aj, "abl", info.from_wxid);
                                    String field_nickname = (String) XposedHelpers.getObjectField(ad, "field_nickname");
                                    String field_city = (String) XposedHelpers.getObjectField(ad, "cCB");
                                    String field_province = (String) XposedHelpers.getObjectField(ad, "cCA");
                                    String signature = (String) XposedHelpers.getObjectField(ad, "signature");
                                    String regionCode = (String) XposedHelpers.getObjectField(ad, "cCG");
                                    int sex = XposedHelpers.getIntField(ad, "sex");

                                    info.from_nickname = field_nickname;
                                    info.timeescape = 0;
                                    info.city = field_city;
                                    info.Country = "CN";
                                    info.sex = sex;
                                    info.province = field_province;
                                    info.signature = signature;
                                    info.displayRegion = regionCode;
                                    //XposedBridge.log("in timer ready wechat info:" + info.toString());
                                    httpRequest(info);
                                    XposedBridge.log("success send wechat info in timer");
                                } catch (Exception e) {
                                    XposedBridge.log("Process normal message first time exception:" + e.toString());
                                }
                                synchronized(msgCacheMap) {
                                    msgCacheMap.remove(key);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    XposedBridge.log("TimerException:" + e.toString());
                }
            }
        }
    };

    public void Init() {
        timer.schedule(task, 1000, 1000);
    }

    void httpRequest(msgCacheInfo info) {
        try {
            String srvAddr = PreferencesUtils.getSrvAddress();
            if (srvAddr == null || srvAddr.length() == 0) {
                return;
            }
            URL url = new URL("http://" + srvAddr + "/wechat/msg/api/v1/send"); //in the real code, there is an ip and a port
            XposedBridge.log("HTTP url:" + url);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept","application/json");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.connect();

            JSONObject data = new JSONObject();
            JSONObject group = new JSONObject();
            group.put("id", info.groupId);
            group.put("nickname", info.groupNickName != null ? info.groupNickName : "");
            JSONObject member = new JSONObject();
            member.put("wxid", info.from_wxid);
            member.put("nickname", info.from_nickname != null ? info.from_nickname : "");
            member.put("groupAliasName", info.from_aliname != null ? info.from_aliname : "");
            member.put("avatar", info.avatar);
            member.put("hdAvatar", info.hdAvatar);
            member.put("sex", info.sex);

            member.put("province", info.province != null ? info.province : "");
            member.put("city", info.city != null ? info.city : "");
            member.put("country", info.Country != null ? info.Country : "");
            member.put("signature", info.signature != null ? info.signature : "");
            member.put("displayRegion", info.displayRegion);

            JSONObject msg = new JSONObject();
            msg.put("id", info.msgId);
            msg.put("type", info.msgType);
            msg.put("img", info.img);
            msg.put("text", info.text !=null ? info.text : "");
            msg.put("sendTime", info.sendTime);
            data.put("group", group);
            data.put("member", member);
            data.put("msg", msg);
            data.put("resource", 2);
            DataOutputStream os = new DataOutputStream(conn.getOutputStream());
            //os.writeBytes(URLEncoder.encode(data.toString(), "UTF-8"));
            //os.writeBytes(data.toString());
            os.write(data.toString().getBytes());

            os.flush();
            os.close();
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK){
                StringBuilder stringBuilder = new StringBuilder();
                InputStreamReader streamReader = new InputStreamReader(conn.getInputStream());
                BufferedReader bufferedReader = new BufferedReader(streamReader);
                String response = null;
                while ((response = bufferedReader.readLine()) != null) {
                    stringBuilder.append(response + "\n");
                }
                bufferedReader.close();
                XposedBridge.log("get response:" + stringBuilder.toString());
            } else {
                XposedBridge.log("Http Error:" + conn.getResponseMessage());
            }

            conn.disconnect();
        } catch (Exception e) {
            XposedBridge.log("HttpException:" + e.toString());
        }
    }

    String uploadFile(String newName, String fileName) {
        String end = "\r\n";
        String twoHyphens = "--";
        String boundary = "----";
        String uploadFile = fileName;
        try {
            String srvAddr = PreferencesUtils.getSrvAddress();
            if (srvAddr == null || srvAddr.length() == 0) {
                return "";
            }
            String actionUrl = "http://" + srvAddr + "/wechat/msg/api/v1/upload";
            XposedBridge.log("HTTP url:" + actionUrl);
            URL url = new URL(actionUrl);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            /* 允许Input、Output，不使用Cache */
            con.setDoInput(true);
            con.setDoOutput(true);
            con.setUseCaches(false);
            /* 设置传送的method=POST */
            con.setRequestMethod("POST");
            /* setRequestProperty */
            con.setRequestProperty("Connection", "Keep-Alive");
            con.setRequestProperty("Charset", "UTF-8");
            con.setRequestProperty("Content-Type",
                    "multipart/form-data;boundary=" + boundary);
            /* 设置DataOutputStream */
            DataOutputStream ds = new DataOutputStream(con.getOutputStream());
            ds.writeBytes(twoHyphens + boundary + end);
            ds.writeBytes("Content-Disposition: form-data; "
                    + "name=\"file\";filename=\"" + newName + "\"" + end);
            ds.writeBytes(end);
            /* 取得文件的FileInputStream */
            FileInputStream fStream = new FileInputStream(uploadFile);
            /* 设置每次写入1024bytes */
            int bufferSize = 1024;
            byte[] buffer = new byte[bufferSize];
            int length = -1;
            /* 从文件读取数据至缓冲区 */
            while ((length = fStream.read(buffer)) != -1) {
                /* 将资料写入DataOutputStream中 */
                ds.write(buffer, 0, length);
            }
            ds.writeBytes(end);
            ds.writeBytes(twoHyphens + boundary + twoHyphens + end);
            /* close streams */
            fStream.close();
            ds.flush();
            /* 取得Response内容 */
            if (con.getResponseCode() == HttpURLConnection.HTTP_OK){
                StringBuilder stringBuilder = new StringBuilder();
                InputStreamReader streamReader = new InputStreamReader(con.getInputStream());
                BufferedReader bufferedReader = new BufferedReader(streamReader);
                String response = null;
                while ((response = bufferedReader.readLine()) != null) {
                    stringBuilder.append(response + "\n");
                }
                bufferedReader.close();
                XposedBridge.log("Upload file get response:" + stringBuilder.toString());
                JSONObject msg = new JSONObject(stringBuilder.toString());
                ds.close();
                return (String)msg.get("data");
            } else {
                XposedBridge.log("Upload file Http Error:" + con.getResponseMessage());
            }
            ds.close();
        } catch (Exception e) {
            XposedBridge.log("UploadFile exception:" + e.toString());
        }
        return null;
    }

    @Override
    public void hook(final XC_LoadPackage.LoadPackageParam lpparam) {
        final Class GetGroupListMethodClass = XposedHelpers.findClass("com.tencent.mm.model.m", lpparam.classLoader);
        GetContactRecordMethodClass = XposedHelpers.findClass(HookParams.getInstance().ContactRecordClassName, lpparam.classLoader);
        final Class GetHeadIconMethodClass = XposedHelpers.findClass(HookParams.getInstance().ContactIconClassName, lpparam.classLoader);
        final Class platformBK = XposedHelpers.findClass("com.tencent.mm.sdk.platformtools.bk", lpparam.classLoader);
        final Class getMediaID = XposedHelpers.findClass("com.tencent.mm.ak.c", lpparam.classLoader);
        final Class getDownLoaderClass = XposedHelpers.findClass("com.tencent.mm.ak.f", lpparam.classLoader);

        final Class TryGetNd = XposedHelpers.findClass("com.tencent.mm.ak.f", lpparam.classLoader);

        final Class TryGetChartroomClass = XposedHelpers.findClass("com.tencent.mm.model.c", lpparam.classLoader);

        //test
        final Class sended = XposedHelpers.findClass("com.tencent.mm.plugin.messenger.a.f", lpparam.classLoader);
        final Class auclass = XposedHelpers.findClass("com.tencent.mm.model.au", lpparam.classLoader);
        final Class ama = XposedHelpers.findClass("com.tencent.mm.model.am.a", lpparam.classLoader);
        //test

        final Class FF = XposedHelpers.findClass("com.tencent.mm.j.f", lpparam.classLoader);
        Class MessengerSyncPara1Class = XposedHelpers.findClass(HookParams.getInstance().MessengerSyncMethodParam1ClassName, lpparam.classLoader);
        Class MessengerSyncPara2Class = XposedHelpers.findClass(HookParams.getInstance().MessengerSyncMethodParam2ClassName, lpparam.classLoader);
        XposedHelpers.findAndHookMethod(HookParams.getInstance().MessengerSyncClassName, lpparam.classLoader, HookParams.getInstance().MessengerSyncMethod, MessengerSyncPara1Class, MessengerSyncPara2Class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                try {

                    Object bi = param.args[0];

                    String field_content = (String) XposedHelpers.getObjectField(bi, "field_content");
                    long field_createTime = XposedHelpers.getLongField(bi, "field_createTime");
                    long field_msgId = XposedHelpers.getLongField(bi, "field_msgId");
                    String field_talker = (String) XposedHelpers.getObjectField(bi, "field_talker");
                    int field_type = XposedHelpers.getIntField(bi, "field_type");

                    /*
                    int czq = XposedHelpers.getIntField(bi, "czq");
                    String czr = (String) XposedHelpers.getObjectField(bi, "czr");
                    long field_bizChatId = XposedHelpers.getLongField(bi, "field_bizChatId");
                    String field_bizChatUserId = (String) XposedHelpers.getObjectField(bi, "field_bizChatUserId");
                    String field_bizClientMsgId = (String) XposedHelpers.getObjectField(bi, "field_bizClientMsgId");
                    int field_flag = XposedHelpers.getIntField(bi, "field_flag");
                    String field_imgPath = (String) XposedHelpers.getObjectField(bi, "field_imgPath");
                    int field_isSend = XposedHelpers.getIntField(bi, "field_isSend");
                    int field_isShowTimer = XposedHelpers.getIntField(bi, "field_isShowTimer");
                    byte[] field_lvbuffer = (byte[]) XposedHelpers.getObjectField(bi, "field_lvbuffer");
                    long field_msgSeq = XposedHelpers.getLongField(bi, "field_msgSeq");
                    long field_msgSvrId = XposedHelpers.getLongField(bi, "field_msgSvrId");
                    String field_reserved = (String) XposedHelpers.getObjectField(bi, "field_reserved");
                    int field_status = XposedHelpers.getIntField(bi, "field_status");
                    int field_talkerId = XposedHelpers.getIntField(bi, "field_talkerId");
                    String field_transBrandWording = (String) XposedHelpers.getObjectField(bi, "field_transBrandWording");
                    String field_transContent = (String) XposedHelpers.getObjectField(bi, "field_transContent");
                    */
                    /*
                    XposedBridge.log("Get contact detail:czq=" + czq +
                            ",czr=" + czr +
                            ",field_bizChatId=" + field_bizChatId +
                            ",field_bizChatUserId=" + field_bizChatUserId +
                            ",field_bizClientMsgId=" + field_bizClientMsgId +
                            ",field_content=" + field_content +
                            ",field_createTime=" + field_createTime +
                            ",field_flag=" + field_flag +
                            ",field_imgPath=" + field_imgPath +
                            ",field_isSend=" + field_isSend +
                            ",field_isShowTimer=" + field_isShowTimer +
                            ",field_lvbuffer=" + field_lvbuffer +
                            ",field_msgId=" + field_msgId +
                            ",field_msgSeq=" + field_msgSeq +
                            ",field_msgSvrId=" + field_msgSvrId +
                            ",field_reserved=" + field_reserved +
                            ",field_status=" + field_status +
                            ",field_talker=" + field_talker +
                            ",field_talkerId=" + field_talkerId +
                            ",field_transBrandWording=" + field_transBrandWording +
                            ",field_transContent=" + field_transContent +
                            ",field_type=" + field_type
                    );*/

                    String wxID = field_talker;
                    if (field_talker.contains("@chatroom")) {
                        //Try(field_talker);
                        wxID = field_content.substring(0, field_content.indexOf(':'));
                        field_content = field_content.substring(field_content.indexOf(':')+2, field_content.length());
                        String displayName = (String) XposedHelpers.callStaticMethod(GetGroupListMethodClass, "P", wxID, field_talker);


                        Object aj = XposedHelpers.callStaticMethod(GetContactRecordMethodClass, "Fw");
                        Object ad = XposedHelpers.callMethod(aj, "abl", wxID);
                        String field_username = (String) XposedHelpers.getObjectField(ad, "field_username");
                        String field_nickname = (String) XposedHelpers.getObjectField(ad, "field_nickname");
                        String field_alias = (String) XposedHelpers.getObjectField(ad, "field_alias");
                        String field_city = (String) XposedHelpers.getObjectField(ad, "cCB");
                        if (field_city == null || field_city.length() == 0) {
                            GetContactInfo(wxID, field_talker);
                        }
                        String field_province = (String) XposedHelpers.getObjectField(ad, "cCA");
                        String signature = (String) XposedHelpers.getObjectField(ad, "signature");
                        String regionCode = (String) XposedHelpers.getObjectField(ad, "cCG");
                        int sex = XposedHelpers.getIntField(ad, "sex");

                        Object iObj = XposedHelpers.callStaticMethod(GetHeadIconMethodClass, HookParams.getInstance().ContactIconMethod);
                        Object iconObj = XposedHelpers.callMethod(iObj, "kp", wxID);
                        String iconBigUrlObj = (String) XposedHelpers.callMethod(iconObj, "JX");
                        String finalUrl = (String) XposedHelpers.callStaticMethod(platformBK, "ZP", iconBigUrlObj);
                        String iconSmallUrlObj = (String) XposedHelpers.callMethod(iconObj, "JY");
                        String finalSmallUrl = (String) XposedHelpers.callStaticMethod(platformBK, "ZP", iconSmallUrlObj);

                        msgCacheInfo info = new msgCacheInfo();
                        info.from_nickname = field_nickname;
                        info.from_wxid = wxID;
                        info.timeescape = 0;
                        info.msgId = field_msgId;
                        info.avatar = finalSmallUrl;
                        info.city = field_city;
                        info.Country = "CN";
                        info.from_aliname = displayName;
                        info.groupId = field_talker;
                        info.hdAvatar = finalUrl;
                        info.msgType = field_type;
                        info.sendTime = field_createTime;
                        info.sex = sex;
                        info.text = field_content;
                        info.province = field_province;
                        info.signature = signature;
                        info.displayRegion = regionCode;

                        if (field_type == 3) {
                            info.text = "";
                            int startIndex = field_content.indexOf("aeskey");
                            int endIndex = field_content.indexOf("/>");
                            String payload = field_content.substring(startIndex, endIndex);
                            //XposedBridge.log("Payload is:" + payload);

                            String[] splited = payload.split(" ");
                            Map<String, String> splitmap = new HashMap<String, String>();
                            for (int index = 0; index < splited.length; index++) {
                                String record = splited[index];
                                String[] eachsplit = record.split("=");
                                if (eachsplit[1].length() > 0) {
                                    splitmap.put(eachsplit[0], eachsplit[1].substring(1, eachsplit[1].length() - 1));
                                }
                            }
                            String imgSavePath = rootPath + "/tencent/MicroMsg/WeiXin/File" + field_msgId + ".jpg";
                            Object obj = XposedHelpers.newInstance(FF);
                            XposedHelpers.setObjectField(obj, "field_fullpath", imgSavePath);
                            XposedHelpers.setIntField(obj, "field_fileType", 1);
                            info.filePath = imgSavePath;

                            String img = "downimg";
                            if (splitmap.containsKey("hdlength")) {
                                XposedHelpers.setIntField(obj, "field_totalLen", Integer.parseInt(splitmap.get("hdlength")));
                                XposedHelpers.setObjectField(obj, "field_fileId", splitmap.get("cdnbigimgurl"));
                                XposedHelpers.setObjectField(obj, "field_aesKey", splitmap.get("aeskey"));
                            } else {
                                XposedHelpers.setIntField(obj, "field_totalLen", Integer.parseInt(splitmap.get("length")));
                                XposedHelpers.setObjectField(obj, "field_fileId", splitmap.get("cdnmidimgurl"));
                                XposedHelpers.setObjectField(obj, "field_aesKey", splitmap.get("aeskey"));
                                XposedHelpers.setIntField(obj, "field_chattype", 1);
                                XposedHelpers.setIntField(obj, "field_fileType", 2);
                            }
                            String mediaId = (String) XposedHelpers.callStaticMethod(getMediaID, "a", img, field_createTime, field_talker, String.valueOf(field_msgId));
                            XposedHelpers.setObjectField(obj, "field_mediaId", mediaId);
                            Object downloadObj = XposedHelpers.callStaticMethod(getDownLoaderClass, "Nd");
                            boolean ret = (boolean) XposedHelpers.callMethod(downloadObj, "b", obj, 1);
                            if (!ret) {
                                XposedBridge.log("call download error");
                                return;
                            }
                            synchronized(msgCacheMap) {
                                msgCacheMap.put(field_msgId, info);
                            }
                            XposedBridge.log("start to download file:" + info.filePath);
                        } else if (info.city == null || info.city.length() == 0){
                            synchronized(msgCacheMap) {
                                msgCacheMap.put(field_msgId, info);
                            }
                        } else {
                            httpRequest(info);
                            XposedBridge.log("send wechat message directly");
                        }
                    }
                } catch (Error | Exception e) {
                    XposedBridge.log("KKK:Exception:" + e.toString());
                    XposedBridge.log("KKK:Exception:" + e.getStackTrace());
                }
            }

            void GetContactInfo(String wxid, String chatroomid) {
                Object obj = XposedHelpers.newInstance(sended, wxid, 1);
                Object auobj = XposedHelpers.callStaticMethod(auclass, "Dk");
                XposedHelpers.callMethod(auobj, "a", obj, 0);

                Object amaobj = XposedHelpers.getStaticObjectField(ama, "dVy");
                XposedHelpers.callMethod(amaobj, "V", wxid, chatroomid);
            }


            void Try(String field_talker) {
                //test 尝试获取陌生人信息
                List<String> members = (List<String>)XposedHelpers.callStaticMethod(GetGroupListMethodClass, "gK", field_talker);
                for(int i=0;i<members.size();i++) {
                    Object aj = XposedHelpers.callStaticMethod(GetContactRecordMethodClass, "Fw");
                    Object ad = XposedHelpers.callMethod(aj, "abl", members.get(i));
                    String field_username = (String) XposedHelpers.getObjectField(ad, "field_username");
                    String field_nickname = (String) XposedHelpers.getObjectField(ad, "field_nickname");
                    String field_alias = (String) XposedHelpers.getObjectField(ad, "field_alias");
                    String field_city = (String) XposedHelpers.getObjectField(ad, "cCB");
                    String field_province = (String) XposedHelpers.getObjectField(ad, "cCA");
                    String signature = (String) XposedHelpers.getObjectField(ad, "signature");
                    String regionCode = (String) XposedHelpers.getObjectField(ad, "cCG");
                    int sex = XposedHelpers.getIntField(ad, "sex");
                    XposedBridge.log("===================try test info:" + field_username + field_city+field_province+field_nickname+field_alias+signature+regionCode+sex);
                    try {
                        Object obj = XposedHelpers.newInstance(sended, members.get(i), 1);
                        Object auobj = XposedHelpers.callStaticMethod(auclass, "Dk");
                        XposedHelpers.callMethod(auobj, "a", obj, 0);

                        Object amaobj = XposedHelpers.getStaticObjectField(ama, "dVy");
                        XposedHelpers.callMethod(amaobj, "V", members.get(i), field_talker);
                        //XposedHelpers.callMethod(amaobj, "X", members.get(i), "");
                        //Thread.sleep(1000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                //test
            }
        });

        /*
        XposedHelpers.findAndHookMethod(HookParams.getInstance().SQLiteDatabaseClassName, lpparam.classLoader, HookParams.getInstance().SQLiteDatabaseUpdateMethod, String.class, ContentValues.class, String.class, String[].class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    String tableName = (String)param.args[0];
                    if (tableName.equals("WxFileIndex2")) {
                        XposedBridge.log("====== table update:" + tableName);
                        ContentValues contentValues = ((ContentValues) param.args[1]);
                        //XposedBridge.log("======= content:" + contentValues.toString());
                        if (contentValues.getAsInteger("msgType") == 3 &&
                                contentValues.getAsInteger("msgSubType") == 20) {
                                Long msgID = contentValues.getAsLong("msgId");
                            synchronized(msgCacheMap) {
                                msgCacheInfo info = msgCacheMap.get(msgID);
                                if (info != null && info.hdFlag == 0) {
                                    info.filePath = rootPath + "/tencent/MicroMsg/" + contentValues.getAsString("path");
                                    XposedBridge.log("Use midimage path:" + info.filePath);
                                }
                            }
                        }
                    }
                } catch (Error | Exception e) {
                    XposedBridge.log("BBBException:"+e.toString());
                }
            }
        });*/

        /*
        XposedHelpers.findAndHookMethod(HookParams.getInstance().SQLiteDatabaseClassName, lpparam.classLoader, HookParams.getInstance().SQLiteDatabaseInsertMethod, String.class, String.class, ContentValues.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    String tableName = (String) param.args[0];

                    XposedBridge.log("---------------- insert table name :" + tableName + "--------------------");

                    if (tableName.equals("WxFileIndex2")) {
                        ContentValues contentValues = ((ContentValues) param.args[2]);
                        XposedBridge.log("======= content:" + contentValues.toString());
                        if (contentValues.getAsInteger("msgType") == 3 &&
                                contentValues.getAsInteger("msgSubType") == 20) {
                            Long msgID = contentValues.getAsLong("msgId");
                            msgCacheInfo info = msgCacheMap.get(msgID);
                            if (info.hdFlag == 0) {
                                info.filePath = rootPath + "/tencent/MicroMsg/" + contentValues.getAsString("path");
                                XposedBridge.log("Use midimage path:" + info.filePath);
                            }
                        }
                    }

                } catch (Error | Exception e) {
                    XposedBridge.log("Insert table callbackError:" + e.toString());
                }
            }
        });*/
    }
}
