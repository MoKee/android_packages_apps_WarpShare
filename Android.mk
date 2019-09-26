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

# Needed by JP2ForAndroid
LOCAL_JNI_SHARED_LIBRARIES += libopenjpeg

# Needed by Glide
LOCAL_STATIC_ANDROID_LIBRARIES += \
    android-support-v4 \
    android-support-v7-appcompat \

LOCAL_STATIC_JAVA_LIBRARIES += $(addprefix warpshare_,$(warpshare_jar_names))
LOCAL_STATIC_JAVA_AAR_LIBRARIES += $(addprefix warpshare_,$(warpshare_aar_names))

LOCAL_STATIC_ANDROID_LIBRARIES += warpshare_MaterialProgressBar

LOCAL_AAPT_FLAGS += \
    --extra-packages android.support.v7.appcompat

LOCAL_PROGUARD_ENABLED := disabled

include $(BUILD_PACKAGE)

include $(CLEAR_VARS)

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := \
    $(foreach jar,$(warpshare_jar_names),warpshare_$(jar):app/libs/$(jar).jar) \
    $(foreach aar,$(warpshare_aar_names),warpshare_$(aar):app/libs/$(aar).aar) \

include $(BUILD_MULTI_PREBUILT)

warpshare_jar_names :=
warpshare_aar_names :=

include $(CLEAR_VARS)

LOCAL_MODULE := warpshare_MaterialProgressBar

LOCAL_MANIFEST_FILE := MaterialProgressBar/library/src/main/AndroidManifest.xml

LOCAL_SRC_FILES := $(call all-java-files-under, MaterialProgressBar/library/src/main/java)

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/MaterialProgressBar/library/src/main/res

LOCAL_USE_AAPT2 := true

LOCAL_STATIC_ANDROID_LIBRARIES += \
    androidx.appcompat_appcompat \
    androidx.annotation_annotation \

include $(BUILD_STATIC_JAVA_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE := libopenjpeg

LOCAL_SRC_FILES_64 := JP2ForAndroid/lib64/libopenjpeg.so
LOCAL_SRC_FILES_32 := JP2ForAndroid/lib/libopenjpeg.so
LOCAL_MULTILIB := both

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_MODULE_SUFFIX := .so

include $(BUILD_PREBUILT)
