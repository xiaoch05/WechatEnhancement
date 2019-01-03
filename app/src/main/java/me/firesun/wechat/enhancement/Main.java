package me.firesun.wechat.enhancement;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import me.firesun.wechat.enhancement.plugin.ADBlock;
import me.firesun.wechat.enhancement.plugin.AntiRevoke;
import me.firesun.wechat.enhancement.plugin.AntiSnsDelete;
import me.firesun.wechat.enhancement.plugin.AutoLogin;
import me.firesun.wechat.enhancement.plugin.HideModule;
import me.firesun.wechat.enhancement.plugin.IPlugin;
import me.firesun.wechat.enhancement.plugin.Limits;
import me.firesun.wechat.enhancement.plugin.LuckMoney;
import me.firesun.wechat.enhancement.plugin.SyncGroupMessage;
import me.firesun.wechat.enhancement.util.HookParams;
import me.firesun.wechat.enhancement.util.SearchClasses;

import static de.robv.android.xposed.XposedBridge.log;

import java.util.List;
import java.util.ArrayList;


public class Main implements IXposedHookLoadPackage {

    private static List<IPlugin> plugins;

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) {
        if (lpparam.packageName.equals(HookParams.WECHAT_PACKAGE_NAME)) {
            try {
                XposedHelpers.findAndHookMethod(ContextWrapper.class, "attachBaseContext", Context.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Context context = (Context) param.args[0];
                        String processName = lpparam.processName;
                        //Only hook important process
                        if (!processName.equals(HookParams.WECHAT_PACKAGE_NAME) &&
                                !processName.equals(HookParams.WECHAT_PACKAGE_NAME + ":tools")
                                ) {
                            return;
                        }
                        String versionName = getVersionName(context, HookParams.WECHAT_PACKAGE_NAME);
                        log("Found wechat version:" + versionName);
                        if (!HookParams.hasInstance()) {
                            SearchClasses.init(context, lpparam, versionName);
                            loadPlugins(lpparam);
                        }
                    }
                });
            } catch (Error | Exception e) {
            }

        }

    }

    private String getVersionName(Context context, String packageName) {
        try {
            PackageManager packageManager = context.getPackageManager();
            PackageInfo packInfo = packageManager.getPackageInfo(packageName, 0);
            return packInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
        }
        return "";
    }


    private void loadPlugins(LoadPackageParam lpparam) {
        plugins = new ArrayList<IPlugin>();
        plugins.add(new ADBlock());
        plugins.add(new AntiRevoke());
        plugins.add(new AntiSnsDelete());
        plugins.add(new AutoLogin());
        plugins.add(new HideModule());
        plugins.add(new LuckMoney());
        plugins.add(new Limits());
        SyncGroupMessage syncPlugin = new SyncGroupMessage();
        syncPlugin.Init();
        plugins.add(syncPlugin);
        for (IPlugin plugin:plugins) {
            try {
                plugin.hook(lpparam);
            } catch (Error | Exception e) {
                log("loadPlugins error" + e);
            }
        }

    }

}
