//@flow

export default class QrScanner {
  onScan: ({ data: string }) => void;

  constructor(
    videoElement: HTMLVideoElement,
    onScan: ({ data: string }) => void
  ) {
    this.onScan = onScan;
  }

  start() {
    this.onScan({ data: "foo" });
  }

  stop() {}
}
