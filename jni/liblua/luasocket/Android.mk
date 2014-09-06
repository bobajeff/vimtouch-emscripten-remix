LOCAL_PATH:= $(call my-dir)

# ========================================================
# lua.o
# ========================================================
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
	src/auxiliar.c \
	src/buffer.c \
	src/except.c \
	src/inet.c \
	src/io.c \
	src/luasocket.c \
	src/mime.c \
	src/options.c \
	src/select.c \
	src/tcp.c \
	src/timeout.c \
	src/udp.c \
	src/unix.c \
	src/usocket.c \
	src/wsocket.c
	
LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)../src/src \
	$(LOCAL_PATH)/src \
	$(LOCAL_PATH)

LOCAL_CFLAGS += \
	-I../../src/src \
	-DLUASOCKET_DEBUG \
	-pedantic \
	-Wall \
	-O2 \
	-fpic


LOCAL_PRELINK_MODULE := false
LOCAL_SHARED_LIBRARIES := libdl liblua

LOCAL_MODULE_TAGS := eng



# ========================================================
include $(call all-makefiles-under,$(LOCAL_PATH))
