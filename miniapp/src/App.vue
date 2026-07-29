<script lang="ts">
export default {
  onLaunch() {
    console.info('cattle miniapp launched')
  },
}
</script>

<style>
page { background: #f3f5f4; color: #202826; font-family: "PingFang SC", sans-serif; font-size: 28rpx; }
button::after { border: 0; }
.safe-page { min-height: 100vh; padding: 32rpx 28rpx calc(32rpx + env(safe-area-inset-bottom)); box-sizing: border-box; }
.panel { background: #fff; border: 1rpx solid #dce3df; border-radius: 10rpx; }
</style>
