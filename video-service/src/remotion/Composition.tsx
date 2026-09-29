import React from 'react';
import {AbsoluteFill, Audio, Composition, Series, staticFile} from 'remotion';
import {SlideScene} from './SlideScene';
import type {Slide} from '../types';

interface CompositionProps {
  slides: Slide[];
  /** Exact duration of each narrated scene at 30 fps. */
  slideDurationsFrames: number[];
}

const SlideComposition = ({slides, slideDurationsFrames}: CompositionProps) => {
  return (
    <AbsoluteFill>
      {/* Keep background music below the narration. */}
      <Audio src={staticFile('bgm.mp3')} volume={0.06} loop />
      <Series>
        {slides.map((slide, index) => (
          <Series.Sequence key={index} durationInFrames={slideDurationsFrames[index] ?? 90}>
            <SlideScene
              slide={slide}
              isActive={true}
              slideIndex={index}
              totalSlides={slides.length}
            />
          </Series.Sequence>
        ))}
      </Series>
    </AbsoluteFill>
  );
};

const defaultSlides: Slide[] = [
  {
    title: 'Giới thiệu về AI trong giáo dục',
    bulletPoints: [
      'Cá nhân hóa lộ trình học tập cho từng học sinh',
      'Tự động xây dựng bài giảng từ tài liệu nguồn',
      'Trình bày kiến thức bằng trợ giảng robot',
    ],
    narrationText:
      'Xin chào. Tôi là trợ giảng AI của EduMind. Trong bài học này, chúng ta sẽ khám phá cách trí tuệ nhân tạo hỗ trợ giáo viên và học sinh.',
  },
  {
    title: 'Quy trình tạo bài giảng',
    bulletPoints: [
      'Giáo viên tải tài liệu PDF hoặc DOCX',
      'AI phân tích và xây dựng kịch bản',
      'Robot thuyết trình với giọng đọc tiếng Việt',
    ],
    narrationText:
      'Quy trình bắt đầu khi giáo viên tải tài liệu lên. Hệ thống phân tích nội dung, tạo kịch bản và kết xuất thành video có trợ giảng robot.',
  },
];

export const RemotionRoot: React.FC = () => {
  const fps = 30;
  const defaultDurationsFrames = defaultSlides.map((slide) =>
    Math.round((Math.max(4000, slide.narrationText.length * 70) / 1000) * fps),
  );
  const totalFrames = defaultDurationsFrames.reduce((sum, duration) => sum + duration, 0);

  return (
    <Composition
      id="SlideComposition"
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      component={SlideComposition as React.ComponentType<any>}
      durationInFrames={totalFrames || 180}
      fps={fps}
      width={1280}
      height={720}
      defaultProps={{slides: defaultSlides, slideDurationsFrames: defaultDurationsFrames}}
    />
  );
};
