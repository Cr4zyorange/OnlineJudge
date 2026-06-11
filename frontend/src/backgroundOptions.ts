import back1 from './assets/back1.jpg';
import back2 from './assets/back2.jpg';
import back3 from './assets/back3.jpg';
import back4 from './assets/back4.jpg';
import back5 from './assets/back5.jpg';
import back6 from './assets/back6.jpg';
import back7 from './assets/back7.jpg';
import liveBackVideo from './assets/live-back.mp4';
import liveBackVideo2 from './assets/live-back2.mp4';
import liveBackVideo3 from './assets/live-back3.mp4';
import liveBackVideo4 from './assets/live-back4.mp4';
import liveBackVideo5 from './assets/live-back5.mp4';
export const BACKGROUND_STORAGE_KEY = 'onlinejudge.backgroundImageId';

export type BackgroundOption = {
  id: string;
  label: string;
  src: string;
  kind?: 'image' | 'video';
};

export const backgroundOptions: BackgroundOption[] = [
  {
    id: '1',
    label: '清透原图',
    src: back1
  },
  {
    id: '2',
    label: '',
    src: back2
  },
  {
    id: '3',
    label: '蓝调晨光',
    src: back3
  },
  {
    id: '4',
    label: '',
    src: back4
  },
    {
    id: '5',
    label: '',
    src: back5
  },
    {
    id: '6',
    label: '',
    src: back6
  },
      {
    id: '7',
    label: '',
    src: back7
  },
  {
    id: 'live-aurora',
    label: 'Live',
    src: liveBackVideo,
    kind: 'video'
  },
  {
    id: 'live-ocean',
    label: 'Live',
    src: liveBackVideo2,
    kind: 'video'
  },
  {
    id: 'live-stars',
    label: 'Live',
    src: liveBackVideo3,
    kind: 'video'
  },
  {
    id: 'live-mist',
    label: 'Live',
    src: liveBackVideo4,
    kind: 'video'
  },
  {
    id: 'live',
    label: 'Live',
    src: liveBackVideo5,
    kind: 'video'
  }
];

export function findBackgroundOption(id: string | null): BackgroundOption {
  return backgroundOptions.find((option) => option.id === id) ?? backgroundOptions[0];
}

export function applyBackgroundOption(option: BackgroundOption) {
  const backgroundImage = option.kind === 'video' ? 'none' : `url("${option.src}")`;
  document.documentElement.style.setProperty('--oj-bg-image', backgroundImage);
  document.body.classList.toggle('oj-video-background', option.kind === 'video');
  document.body.classList.remove('oj-live-background');
}
