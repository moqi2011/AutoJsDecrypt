package cn.l2u.autojsdecrypt;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import dalvik.system.DexClassLoader;

public class Tools {

    private Method decrypt;
    private Object insScriptEncryption;

    public void init(String dexPath, String configJson) throws Exception {
        DexClassLoader loader = new DexClassLoader(dexPath, null, null, Tools.class.getClassLoader());

        Class<?> clsProjectConfigCompanion = loader.loadClass("com.stardust.autojs.project.ProjectConfig$Companion");

        Object insProjectConfig = clsProjectConfigCompanion.getMethod("fromJson", String.class).invoke(clsProjectConfigCompanion.getConstructor().newInstance(), configJson);

        Class<?> clsScriptEncryption = loader.loadClass("com.stardust.autojs.engine.encryption.ScriptEncryption");

        insScriptEncryption = clsScriptEncryption.getField("sInstance").get(null);

        //initFingerprint
        clsScriptEncryption.getMethod("initFingerprint", insProjectConfig.getClass()).invoke(insScriptEncryption, insProjectConfig);

        decrypt = clsScriptEncryption.getMethod("decrypt", byte[].class, int.class, int.class);
    }

    public byte[] decrypt(byte[] data) throws InvocationTargetException, IllegalAccessException {
        return (byte[]) decrypt.invoke(insScriptEncryption, data, 8, data.length);
    }
}
