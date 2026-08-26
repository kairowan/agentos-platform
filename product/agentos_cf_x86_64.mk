$(call inherit-product, device/google/cuttlefish/vsoc_x86_64/phone/aosp_cf.mk)

PRODUCT_NAME := agentos_cf_x86_64
PRODUCT_DEVICE := vsoc_x86_64
PRODUCT_BRAND := AgentOS
PRODUCT_MODEL := AgentOS Cuttlefish
PRODUCT_MANUFACTURER := AgentOS

PRODUCT_PACKAGES += \
    AgentShell \
    AgentCapabilityService \
    AgentMediaService \
    AgentVoiceService

PRODUCT_PACKAGE_OVERLAYS += vendor/agentos/overlay

PRODUCT_PRIVATE_SEPOLICY_DIRS += vendor/agentos/sepolicy/private
