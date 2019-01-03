package me.firesun.wechat.enhancement.plugin;

import android.content.ContentValues;


import org.bouncycastle.jce.provider.X509StoreLDAPCertPairs;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import me.firesun.wechat.enhancement.PreferencesUtils;
import me.firesun.wechat.enhancement.util.HookParams;

import android.os.Environment;
import org.xml.sax.XMLReader;

import java.util.Timer;
import java.util.TimerTask;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.net.URLEncoder;

import java.io.BufferedReader;


public class SyncGroupMessage implements IPlugin {
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
                    + "<text=" + text + ">";
        }
    }
    private static List<msgCacheInfo> msgCacheMap = new ArrayList<msgCacheInfo>();
    Timer timer = new Timer(true);
    String rootPath = Environment.getExternalStorageDirectory().getAbsolutePath();
    TimerTask task = new TimerTask() {
        public void run() {
            //XposedBridge.log("This is timer");
            try {
                for (int idx = 0; idx < msgCacheMap.size(); idx++) {
                    msgCacheInfo info = msgCacheMap.get(idx);
                    if (info.timeescape < 5) {
                        info.timeescape++;
                        continue;
                    } else if (info.timeescape < 30){
                        info.timeescape++;
                        XposedBridge.log("I should find file");
                        String imgSavePath = rootPath + "/tencent/MicroMsg/WeiXin/File" + info.msgId + ".jpg";
                        File file = new File(imgSavePath);
                        if (file.exists()) {
                            XposedBridge.log("file has been download finished:" + imgSavePath);
                            msgCacheMap.remove(idx);
                            idx--;
                            httpRequest(info);
                        }
                    } else {
                        msgCacheMap.remove(idx);
                        idx--; // 先break出来防止异常
                    }
                }
            } catch (Exception e) {
                XposedBridge.log("TimerException:" + e.toString());
            }
        }
    };

    public void Init() {
        timer.schedule(task, 1000, 1000);
    }

    void httpRequest(msgCacheInfo info) {
        try {
            URL url = new URL("http://192.168.1.158:8080/wechat/msg/api/v1/hello"); //in the real code, there is an ip and a port
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept","application/json");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.connect();

            JSONObject data = new JSONObject();
            JSONObject group = new JSONObject();
            group.put("id", info.groupId);
            group.put("nickname", info.groupNickName);
            JSONObject member = new JSONObject();
            member.put("wxid", info.from_wxid);
            member.put("nickname", URLEncoder.encode(info.from_nickname, "UTF-8"));
            member.put("groupAliasName", info.from_aliname);
            member.put("avatar", info.avatar);
            member.put("hdAvatar", info.hdAvatar);
            member.put("sex", info.sex);
            member.put("province", info.province);
            member.put("city", info.city);
            member.put("country", info.Country);
            member.put("signature", info.signature);
            member.put("displayRegion", info.displayRegion);
            JSONObject msg = new JSONObject();
            msg.put("id", info.msgId);
            msg.put("type", info.msgType);
            msg.put("img", "0xccc");
            msg.put("text", info.text);
            msg.put("sendTime", info.sendTime);
            data.put("group", group);
            data.put("member", member);
            data.put("msg", msg);
            data.put("resource", 2);
            DataOutputStream os = new DataOutputStream(conn.getOutputStream());
            //os.writeBytes(URLEncoder.encode(data.toString(), "UTF-8"));
            os.writeBytes(data.toString());

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

    @Override
    public void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        final Class GetGroupListMethodClass = XposedHelpers.findClass("com.tencent.mm.model.m", lpparam.classLoader);
        final Class GetContactRecordMethodClass = XposedHelpers.findClass(HookParams.getInstance().ContactRecordClassName, lpparam.classLoader);
        final Class GetHeadIconMethodClass = XposedHelpers.findClass(HookParams.getInstance().ContactIconClassName, lpparam.classLoader);
        final Class platformBK = XposedHelpers.findClass("com.tencent.mm.sdk.platformtools.bk", lpparam.classLoader);
        final Class getMediaID = XposedHelpers.findClass("com.tencent.mm.ak.c", lpparam.classLoader);
        final Class getDownLoaderClass = XposedHelpers.findClass("com.tencent.mm.ak.f", lpparam.classLoader);

        final Class TryGetNd = XposedHelpers.findClass("com.tencent.mm.ak.f", lpparam.classLoader);

        final Class FF = XposedHelpers.findClass("com.tencent.mm.j.f", lpparam.classLoader);
        Class MessengerSyncPara1Class = XposedHelpers.findClass(HookParams.getInstance().MessengerSyncMethodParam1ClassName, lpparam.classLoader);
        Class MessengerSyncPara2Class = XposedHelpers.findClass(HookParams.getInstance().MessengerSyncMethodParam2ClassName, lpparam.classLoader);
        XposedHelpers.findAndHookMethod(HookParams.getInstance().MessengerSyncClassName, lpparam.classLoader, HookParams.getInstance().MessengerSyncMethod, MessengerSyncPara1Class, MessengerSyncPara2Class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                try {
                    if (!PreferencesUtils.isAntiRevoke()) {
                        return;
                    }

                    Object bi = param.args[0];
                    int czq = XposedHelpers.getIntField(bi, "czq");
                    String czr = (String) XposedHelpers.getObjectField(bi, "czr");
                    long field_bizChatId = XposedHelpers.getLongField(bi, "field_bizChatId");
                    String field_bizChatUserId = (String) XposedHelpers.getObjectField(bi, "field_bizChatUserId");
                    String field_bizClientMsgId = (String) XposedHelpers.getObjectField(bi, "field_bizClientMsgId");
                    String field_content = (String) XposedHelpers.getObjectField(bi, "field_content");
                    long field_createTime = XposedHelpers.getLongField(bi, "field_createTime");
                    int field_flag = XposedHelpers.getIntField(bi, "field_flag");
                    String field_imgPath = (String) XposedHelpers.getObjectField(bi, "field_imgPath");
                    int field_isSend = XposedHelpers.getIntField(bi, "field_isSend");
                    int field_isShowTimer = XposedHelpers.getIntField(bi, "field_isShowTimer");
                    byte[] field_lvbuffer = (byte[]) XposedHelpers.getObjectField(bi, "field_lvbuffer");
                    long field_msgId = XposedHelpers.getLongField(bi, "field_msgId");
                    long field_msgSeq = XposedHelpers.getLongField(bi, "field_msgSeq");
                    long field_msgSvrId = XposedHelpers.getLongField(bi, "field_msgSvrId");
                    String field_reserved = (String) XposedHelpers.getObjectField(bi, "field_reserved");
                    int field_status = XposedHelpers.getIntField(bi, "field_status");
                    String field_talker = (String) XposedHelpers.getObjectField(bi, "field_talker");
                    int field_talkerId = XposedHelpers.getIntField(bi, "field_talkerId");
                    String field_transBrandWording = (String) XposedHelpers.getObjectField(bi, "field_transBrandWording");
                    String field_transContent = (String) XposedHelpers.getObjectField(bi, "field_transContent");
                    int field_type = XposedHelpers.getIntField(bi, "field_type");
                    XposedBridge.log("yyyyyyyyyyyyy:czq=" + czq +
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
                    );

                    String wxID = field_talker;
                    if (field_talker.contains("@chatroom")) {
                        wxID = field_content.substring(0, field_content.indexOf(':'));
                        XposedBridge.log("Get wxID:" + wxID);
                        String displayName = (String) XposedHelpers.callStaticMethod(GetGroupListMethodClass, "P", wxID, field_talker);
                        Object aj = XposedHelpers.callStaticMethod(GetContactRecordMethodClass, "Fw");
                        XposedBridge.log("Object is:" + aj);

                        Object ad = XposedHelpers.callMethod(aj, "abl", wxID);
                        XposedBridge.log("ad is:" + ad);

                        String field_username = (String) XposedHelpers.getObjectField(ad, "field_username");
                        XposedBridge.log("Get field_username:" + field_username);

                        String field_nickname = (String) XposedHelpers.getObjectField(ad, "field_nickname");
                        XposedBridge.log("Get field_nickname:" + field_nickname);

                        String field_alias = (String) XposedHelpers.getObjectField(ad, "field_alias");
                        XposedBridge.log("Get field_alias:" + field_alias);

                        String field_city = (String) XposedHelpers.getObjectField(ad, "cCB");
                        XposedBridge.log("Get field_city:" + field_city);

                        String field_province = (String) XposedHelpers.getObjectField(ad, "cCA");
                        XposedBridge.log("Get field_province:" + field_province);

                        String signature = (String) XposedHelpers.getObjectField(ad, "signature");
                        XposedBridge.log("Get signature:" + signature);

                        String regionCode = (String) XposedHelpers.getObjectField(ad, "cCG");
                        XposedBridge.log("Get regionCode:" + regionCode);

                        int sex = XposedHelpers.getIntField(ad, "sex");
                        XposedBridge.log("Get sex:" + sex);

                        Object iObj = XposedHelpers.callStaticMethod(GetHeadIconMethodClass, HookParams.getInstance().ContactIconMethod);
                        XposedBridge.log("Get object i:" + iObj);
                        Object iconObj = XposedHelpers.callMethod(iObj, "kp", wxID);
                        XposedBridge.log("Get object icon:" + iconObj);
                        String iconBigUrlObj = (String) XposedHelpers.callMethod(iconObj, "JX");
                        XposedBridge.log("iconBigUrlObj:" + iconBigUrlObj);
                        String finalUrl = (String) XposedHelpers.callStaticMethod(platformBK, "ZP", iconBigUrlObj);
                        XposedBridge.log("finalBigUrl:" + finalUrl);
                        String iconSmallUrlObj = (String) XposedHelpers.callMethod(iconObj, "JY");
                        XposedBridge.log("iconSmallUrlObj:" + iconSmallUrlObj);
                        String finalSmallUrl = (String) XposedHelpers.callStaticMethod(platformBK, "ZP", iconSmallUrlObj);
                        XposedBridge.log("finalSmallUrl:" + finalSmallUrl);

                        msgCacheInfo info = new msgCacheInfo();
                        info.from_nickname = field_nickname;
                        info.from_wxid = wxID;
                        info.timeescape = 0;
                        info.msgId = field_msgId;
                        info.avatar = finalSmallUrl;
                        info.city = field_city;
                        info.Country = "CN";
                        info.from_aliname = field_alias;
                        info.groupId = field_talker;
                        info.hdAvatar = finalUrl;
                        info.msgType = field_type;
                        info.sendTime = field_createTime;
                        info.sex = sex;
                        info.text = field_content;

                        XposedBridge.log("ready wechat info:" + info.toString());

                        String urf8fmt = URLEncoder.encode(info.from_nickname, "UTF-8");
                        String origfmt = URLDecoder.decode(urf8fmt, "UTF-8");
                        XposedBridge.log("Nickname:" + origfmt);

                        if (field_type == 3) {
                            msgCacheMap.add(info);
                            int startIndex = field_content.indexOf("aeskey");
                            int endIndex = field_content.indexOf("/>");
                            String payload = field_content.substring(startIndex, endIndex);
                            XposedBridge.log("Payload is:" + payload);

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

                            if (splitmap.containsKey("hdlength")) {
                                XposedHelpers.setIntField(obj, "field_totalLen", Integer.parseInt(splitmap.get("hdlength")));
                                XposedHelpers.setObjectField(obj, "field_fileId", splitmap.get("cdnbigimgurl"));
                            } else {
                                XposedHelpers.setIntField(obj, "field_totalLen", Integer.parseInt(splitmap.get("length")));
                                XposedHelpers.setObjectField(obj, "field_fileId", splitmap.get("cdnmidimgurl"));
                            }

                            XposedHelpers.setObjectField(obj, "field_aesKey", splitmap.get("aeskey"));

                            String mediaId = (String) XposedHelpers.callStaticMethod(getMediaID, "a", "downimg", field_createTime, field_talker, String.valueOf(field_msgId));
                            XposedHelpers.setObjectField(obj, "field_mediaId", mediaId);
                            Object downloadObj = XposedHelpers.callStaticMethod(getDownLoaderClass, "Nd");
                            boolean ret = (boolean) XposedHelpers.callMethod(downloadObj, "b", obj, -1);
                            if (!ret) {
                                XposedBridge.log("call download error");
                            }
                            XposedBridge.log("download file finished");
                        } else {
                            httpRequest(info);
                        }
                    }
                } catch (Error | Exception e) {
                    XposedBridge.log("KKK:Exception:" + e.toString());
                    XposedBridge.log("KKK:Exception:" + e.getStackTrace());
                }
            }
        });
    }
}
