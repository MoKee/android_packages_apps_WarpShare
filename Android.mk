#
# Copyright (C) 2019 The MoKee Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

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

LOCAL_DEX_PREOPT := false

LOCAL_STATIC_ANDROID_LIBRARIES += \
    androidx.core_core \
    androidx.recyclerview_recyclerview \
    com.google.android.material_material \

# Needed by JP2ForAndroid
LOCAL_JNI_SHARED_LIBRARIES += libopenjpeg

# Needed by Glide
LOCAL_STATIC_ANDROID_LIBRARIES += \
    android-support-fragment \
    android-support-animatedvectordrawable \

# Needed by Project Rome
LOCAL_STATIC_JAVA_LIBRARIES += AndroidCll
LOCAL_JNI_SHARED_LIBRARIES += \
    libc++_shared \
    libcdp_one_sdk_android.1.3.0 \

LOCAL_STATIC_JAVA_LIBRARIES += $(addprefix warpshare_,$(warpshare_jar_names))
LOCAL_STATIC_JAVA_AAR_LIBRARIES += $(addprefix warpshare_,$(warpshare_aar_names))

LOCAL_STATIC_ANDROID_LIBRARIES += warpshare_MaterialProgressBar

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_AAPT_FLAGS += \
    --extra-packages androidx.preference

LOCAL_RESOURCE_DIR += \
    $(LOCAL_PATH)/androidx.preference/res

LOCAL_JAVA_LIBRARIES := mokee-cloud

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

include $(CLEAR_VARS)

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := \
    AndroidCll:ProjectRome/AndroidCll.jar

include $(BUILD_MULTI_PREBUILT)

include $(CLEAR_VARS)

LOCAL_MODULE := libc++_shared

LOCAL_SRC_FILES_64 := ProjectRome/lib64/libc++_shared.so
LOCAL_SRC_FILES_32 := ProjectRome/lib/libc++_shared.so
LOCAL_MULTILIB := both

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_MODULE_SUFFIX := .so

include $(BUILD_PREBUILT)

include $(CLEAR_VARS)

LOCAL_MODULE := libcdp_one_sdk_android.1.3.0

LOCAL_SRC_FILES_64 := ProjectRome/lib64/libcdp_one_sdk_android.1.3.0.so
LOCAL_SRC_FILES_32 := ProjectRome/lib/libcdp_one_sdk_android.1.3.0.so
LOCAL_MULTILIB := both

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_MODULE_SUFFIX := .so

include $(BUILD_PREBUILT)
