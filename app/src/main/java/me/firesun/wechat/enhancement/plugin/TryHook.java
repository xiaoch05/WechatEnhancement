package me.firesun.wechat.enhancement.plugin;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import me.firesun.wechat.enhancement.PreferencesUtils;
import me.firesun.wechat.enhancement.util.HookParams;
import me.firesun.wechat.enhancement.util.XmlToJson;

import static android.text.TextUtils.isEmpty;
import static android.widget.Toast.LENGTH_LONG;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findFirstFieldByExactType;
import android.content.Intent;


public class TryHook implements IPlugin {
    @Override
    public void hook(final XC_LoadPackage.LoadPackageParam lpparam) {
        Class bnkClass = XposedHelpers.findClass("com.tencent.mm.protocal.c.bnk", lpparam.classLoader);
        XposedHelpers.findAndHookMethod("com.tencent.mm.model.j", lpparam.classLoader, "a", Intent.class, bnkClass, int.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    XposedBridge.log("========com.tencent.mm.protocal.c.bnk========");
                } catch (Error | Exception e) {
                }
            }
        });

        Class bnmClass = XposedHelpers.findClass("com.tencent.mm.protocal.c.bnm", lpparam.classLoader);
        XposedHelpers.findAndHookMethod("com.tencent.mm.model.j", lpparam.classLoader, "a", Intent.class, bnmClass, int.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    XposedBridge.log("========com.tencent.mm.protocal.c.bnm========");
                    Throwable ex = new Throwable();
                    StackTraceElement[] stackElements = ex.getStackTrace();
                    if (stackElements != null) {
                        XposedBridge.log("-----------------print stack------------------");
                        XposedBridge.log("args:" + param.args[0]);
                        for (int i = 0; i < stackElements.length; i++) {
                            XposedBridge.log(stackElements[i].getClassName() + "/t");
                            XposedBridge.log(stackElements[i].getFileName() + "/t");
                            XposedBridge.log(stackElements[i].getLineNumber() + "/t");
                            XposedBridge.log(stackElements[i].getMethodName());
                        }
                    }
                } catch (Error | Exception e) {
                }
            }
        });

        Class fClass = XposedHelpers.findClass("com.tencent.mm.j.f", lpparam.classLoader);
        XposedHelpers.findAndHookMethod("com.tencent.mm.ak.b", lpparam.classLoader, "b", fClass, int.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    XposedBridge.log("========com.tencent.mm.ak.b========");

                    Object msg = param.args[0];
                    String field_fullpath = (String)XposedHelpers.getObjectField(msg, "field_fullpath");
                    int field_fileType = XposedHelpers.getIntField(msg, "field_fileType");
                    int field_totalLen = XposedHelpers.getIntField(msg, "field_totalLen");
                    String field_fileId = (String)XposedHelpers.getObjectField(msg, "field_fileId");
                    String field_aesKey = (String)XposedHelpers.getObjectField(msg, "field_aesKey");
                    int field_chattype = XposedHelpers.getIntField(msg, "field_chattype");

                    XposedBridge.log("content: field_fullpath=" + field_fullpath +
                            ",field_fileType=" + field_fileType +
                            ",field_totalLen=" + field_totalLen +
                            ",field_fileId=" + field_fileId +
                            ",field_aesKey="+field_aesKey+
                            ",field_chattype" + field_chattype);

                    /*
                    for (String key: ((Bundle)param.args[0]).keySet())
                    {
                        String value = ((Bundle)param.args[0]).getString(key);
                        XposedBridge.log("Bundle Content Key=" + key + ", content=" + value);
                    }*/

                } catch (Error | Exception e) {
                }
            }
        });
    }
}