export default class QrScanner {
  onScan: ({ data }: { data: string }) => void;

  constructor(
    videoElement: HTMLVideoElement,
    onScan: ({ data }: { data: string }) => void
  ) {
    this.onScan = onScan;
  }

  start(): void {
    this.onScan({ data: "foo" });
  }

  stop(): void {}
}
