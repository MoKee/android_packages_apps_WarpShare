LOCAL_PATH := $(call my-dir)

warpshare_jar_names := $(basename $(notdir $(call all-named-files-under,*.jar,app/libs/)))
warpshare_aar_names := $(basename $(notdir $(call all-named-files-under,*.aar,app/libs/)))

include $(CLEAR_VARS)

LOCAL_PACKAGE_NAME := WarpShare

LOCAL_CERTIFICATE := platform
LOCAL_PRIVILEGED_MODULE := true
LOCAL_PRIVATE_PLATFORM_APIS := true

LOCAL_MANIFEST_FILE := app/src/main/AndroidManifest.xml

LOCAL_SRC_FILES := $(call all-java-files-under, app/src/main/java)

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/app/src/main/res

LOCAL_USE_AAPT2 := true

LOCAL_STATIC_ANDROID_LIBRARIES += \
    androidx.preference_preference \
    androidx.recyclerview_recyclerview \
    com.google.android.material_material \

LOCAL_STATIC_JAVA_LIBRARIES += $(addprefix warpshare_,$(warpshare_jar_names))
LOCAL_STATIC_JAVA_AAR_LIBRARIES += $(addprefix warpshare_,$(warpshare_aar_names))

include $(BUILD_PACKAGE)

include $(CLEAR_VARS)

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := \
    $(foreach jar,$(warpshare_jar_names),warpshare_$(jar):app/libs/$(jar).jar) \
    $(foreach aar,$(warpshare_aar_names),warpshare_$(aar):app/libs/$(aar).aar) \

include $(BUILD_MULTI_PREBUILT)

warpshare_jar_names :=
warpshare_aar_names :=
