import React from 'react';
import {Img, staticFile, Video} from 'remotion';

interface AvatarProps {
  audioSrc?: string | null;
  avatarVideoUrl?: string | null;
  teacherAction?: string | null;
  style?: React.CSSProperties;
}

const poseForAction = (teacherAction?: string | null): string => {
  const action = (teacherAction ?? 'EXPLAIN').toUpperCase();
  if (action === 'QUESTION' || action === 'WELCOME') {
    return 'teacher/avatar_teacher_question.png';
  }
  if (action === 'POINT') {
    return 'teacher/avatar_teacher_point.png';
  }
  return 'teacher/avatar_teacher_explain.png';
};

/**
 * Uses the generated talking-teacher video when Avatar AI succeeds. If that
 * service is unavailable, the fallback remains a 3D teacher, never a robot.
 */
export const Avatar: React.FC<AvatarProps> = ({
  audioSrc,
  avatarVideoUrl,
  teacherAction,
  style,
}) => {
  if (avatarVideoUrl) {
    return (
      <div
        style={{
          width: 330,
          height: 330,
          borderRadius: 24,
          overflow: 'hidden',
          position: 'relative',
          border: '2px solid rgba(56, 189, 248, 0.4)',
          boxShadow: '0 20px 50px rgba(2, 6, 23, 0.6), 0 0 30px rgba(56, 189, 248, 0.25)',
          background: 'linear-gradient(135deg, rgba(15, 23, 42, 0.8), rgba(30, 27, 75, 0.8))',
          backdropFilter: 'blur(12px)',
          ...style,
        }}
      >
        <Video
          src={avatarVideoUrl}
          muted
          volume={0}
          style={{width: '100%', height: '100%', objectFit: 'contain'}}
        />
        <AvatarBadge label="3D TEACHER" />
      </div>
    );
  }

  if (!audioSrc) {
    return <TeacherArtwork teacherAction={teacherAction} style={style} />;
  }

  // If the AI talking-head service is unavailable, keep a natural teaching
  // pose. Do not fake speech by bobbing/scaling the whole artwork: that looks
  // like a GIF and cannot match phonemes.
  return <TeacherArtwork teacherAction={teacherAction} style={style} />;
};

const TeacherArtwork: React.FC<{
  teacherAction?: string | null;
  style?: React.CSSProperties;
}> = ({teacherAction, style}) => {
  return (
    <div
      style={{
        width: 330,
        height: 350,
        position: 'relative',
        overflow: 'hidden',
        borderRadius: 24,
        transformOrigin: '50% 100%',
        border: '2px solid rgba(56, 189, 248, 0.4)',
        boxShadow: '0 20px 50px rgba(2, 6, 23, 0.6), 0 0 30px rgba(56, 189, 248, 0.25)',
        background: 'linear-gradient(135deg, rgba(15, 23, 42, 0.8), rgba(30, 27, 75, 0.8))',
        ...style,
      }}
    >
      <div
        style={{
          position: 'absolute',
          inset: 0,
          opacity: 0.22,
          background: 'radial-gradient(circle at 50% 38%, #38bdf8 0, transparent 58%)',
        }}
      />
      <Img
        src={staticFile(poseForAction(teacherAction))}
        style={{
          position: 'absolute',
          inset: 0,
          width: '100%',
          height: '100%',
          objectFit: 'contain',
          objectPosition: 'center bottom',
          filter: 'drop-shadow(0 18px 18px rgba(2, 6, 23, .45))',
        }}
      />
      <AvatarBadge label="3D TEACHER" />
    </div>
  );
};

const AvatarBadge: React.FC<{label: string}> = ({label}) => (
  <div
    style={{
      position: 'absolute',
      top: 12,
      right: 12,
      padding: '4px 10px',
      borderRadius: 999,
      background: 'rgba(15, 23, 42, 0.75)',
      border: '1px solid rgba(56, 189, 248, 0.5)',
      color: '#38bdf8',
      fontSize: 10,
      fontWeight: 800,
      letterSpacing: 1,
    }}
  >
    ● {label}
  </div>
);
