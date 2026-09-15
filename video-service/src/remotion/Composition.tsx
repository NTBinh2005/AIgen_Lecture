import React from 'react';
import { Composition, AbsoluteFill, Audio, staticFile } from 'remotion';
import { TransitionSeries, linearTiming } from '@remotion/transitions';
import { slide as slideTransition } from '@remotion/transitions/slide';
import { SlideScene } from './SlideScene';
import type { Slide } from '../types';

interface CompositionProps {
  slides: Slide[];
  /** Thời lượng mỗi slide tính bằng số frames (fps=30) */
  slideDurationsFrames: number[];
}

/**
 * SlideComposition — wrap từng slide thành TransitionSeries.Sequence, mỗi slide
 * có thời lượng riêng (tính từ TTS durationMs, quy đổi ra frames).
 * Áp dụng hiệu ứng Fade giữa các slide và thêm Nhạc nền Lofi.
 */
const SlideComposition = ({
  slides,
  slideDurationsFrames,
}: CompositionProps) => {
  return (
    <AbsoluteFill>
      {/* Nhạc nền chạy lặp lại xuyên suốt video với âm lượng 15% */}
      <Audio src={staticFile('bgm.mp3')} volume={0.15} loop />
      
      <TransitionSeries>
      {slides.map((slide, i) => (
        <React.Fragment key={i}>
          <TransitionSeries.Sequence durationInFrames={slideDurationsFrames[i] ?? 90}>
            <SlideScene slide={slide} isActive={true} />
          </TransitionSeries.Sequence>
          {i < slides.length - 1 && (
            <TransitionSeries.Transition
              presentation={slideTransition()}
              timing={linearTiming({ durationInFrames: 30 })}
            />
          )}
        </React.Fragment>
      ))}
    </TransitionSeries>
    </AbsoluteFill>
  );
};

/** Default props dùng cho Remotion Studio preview */
const defaultSlides: Slide[] = [
  {
    title: 'Giới thiệu về AI trong Giáo dục',
    bulletPoints: [
      'AI giúp cá nhân hóa lộ trình học tập',
      'Tự động sinh bài giảng từ tài liệu',
      'Phân tích dữ liệu học tập theo thời gian thực',
    ],
    narrationText:
      'Trí tuệ nhân tạo đang thay đổi cách chúng ta giảng dạy và học tập. Từ việc cá nhân hóa lộ trình cho từng học sinh, đến tự động tạo bài giảng từ tài liệu thô, AI mở ra một kỷ nguyên giáo dục mới.',
  },
  {
    title: 'Quy trình tạo bài giảng',
    bulletPoints: [
      'Teacher upload PDF hoặc DOCX',
      'AI trích xuất và phân tích nội dung',
      'Remotion render video bài giảng',
    ],
    narrationText:
      'Quy trình bắt đầu khi giáo viên tải lên tài liệu. Hệ thống AI sẽ phân tích nội dung và tự động tạo ra một video bài giảng hoàn chỉnh với hình ảnh và giọng đọc.',
  },
];

export const RemotionRoot: React.FC = () => {
  const fps = 30;
  const defaultDurationsFrames = defaultSlides.map((slide) =>
    Math.round((Math.max(3000, slide.narrationText.length * 60) / 1000) * fps),
  );
  const totalFrames = defaultDurationsFrames.reduce((a, b) => a + b, 0);

  return (
    <Composition
      id="SlideComposition"
      // Cast cần thiết để thỏa mãn kiểu LooseComponentType<Record<string, unknown>> của Remotion.
      // An toàn vì inputProps và defaultProps luôn truyền đúng kiểu CompositionProps.
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      component={SlideComposition as React.ComponentType<any>}
      durationInFrames={totalFrames || 180}
      fps={fps}
      width={1280}
      height={720}
      defaultProps={{
        slides: defaultSlides,
        slideDurationsFrames: defaultDurationsFrames,
      }}
    />
  );
};
