/**
 * Remotion entry point — import tất cả compositions tại đây.
 * File này được dùng bởi Remotion CLI (studio & render).
 */
export { RemotionRoot as RemotionRoot } from './Composition';

// Re-export để Remotion CLI tìm thấy composition:
import { registerRoot } from 'remotion';
import { RemotionRoot } from './Composition';
registerRoot(RemotionRoot);
