export interface SectionOffsetSource {
  readonly sectionStartPosition: number
  readonly dataStartPosition: number
}

export interface Vector2 {
  readonly x: number
  readonly y: number
}

export interface Vector3 {
  readonly x: number
  readonly y: number
  readonly z: number
}

export interface BgraColor {
  readonly b: number
  readonly g: number
  readonly r: number
  readonly a: number
}

function rotateRight8(value: number, amount: number): number {
  const shift = ((amount % 8) + 8) % 8
  return ((value >>> shift) | (value << (8 - shift))) & 0xff
}

export class ByteReader {
  readonly bytes: Uint8Array
  readonly resourceName: string
  position = 0

  constructor(bytes: Uint8Array, resourceName = '') {
    this.bytes = bytes
    this.resourceName = resourceName
  }

  nextString(length: number): string {
    let result = ''
    for (let i = 0; i < length; i += 1) {
      result += this.nextChar()
    }
    return result
  }

  next8(): number {
    const value = this.bytes[this.position] ?? 0
    this.position += 1
    return value
  }

  next8Signed(): number {
    const value = this.next8()
    return value > 0x7f ? value - 0x100 : value
  }

  next16(): number {
    return this.next8() | (this.next8() << 8)
  }

  read16(offset: number): number {
    return (this.bytes[offset] ?? 0) | ((this.bytes[offset + 1] ?? 0) << 8)
  }

  next16Signed(): number {
    const value = this.next16()
    return value > 0x7fff ? value - 0x10000 : value
  }

  next32(): number {
    return (this.next16() | (this.next16() << 16)) >>> 0
  }

  next32Signed(): number {
    const value = this.next32()
    return value > 0x7fffffff ? value - 0x100000000 : value
  }

  next16BE(): number {
    return (this.next8() << 8) | this.next8()
  }

  next32BE(): number {
    return ((this.next16BE() << 16) | this.next16BE()) >>> 0
  }

  next64BE(): bigint {
    const high = BigInt(this.next32BE())
    const low = BigInt(this.next32BE())
    return (high << 32n) | low
  }

  nextFloat(): number {
    const view = new DataView(new ArrayBuffer(4))
    view.setUint32(0, this.next32(), true)
    return view.getFloat32(0, true)
  }

  next32Array(amount: number): number[] {
    return Array.from({ length: amount }, () => this.next32())
  }

  next32SignedArray(amount: number): number[] {
    return Array.from({ length: amount }, () => this.next32Signed())
  }

  nextFloatArray(amount: number): number[] {
    return Array.from({ length: amount }, () => this.nextFloat())
  }

  nextVector2f(): Vector2 {
    return {
      x: this.nextFloat(),
      y: this.nextFloat(),
    }
  }

  nextVector3f(): Vector3 {
    return {
      x: this.nextFloat(),
      y: this.nextFloat(),
      z: this.nextFloat(),
    }
  }

  nextBGRA(): BgraColor {
    return {
      b: this.next8(),
      g: this.next8(),
      r: this.next8(),
      a: this.next8(),
    }
  }

  subBuffer(size: number): Uint8Array
  subBuffer(offset: number, size: number): Uint8Array
  subBuffer(offsetOrSize: number, size?: number): Uint8Array {
    const offset = size === undefined ? this.position : offsetOrSize
    const length = size === undefined ? offsetOrSize : size
    return this.bytes.slice(offset, offset + length)
  }

  nextChar(): string {
    return String.fromCharCode(this.next8())
  }

  nextCharJis(): string {
    let code = this.next8()
    if ((code & 0x80) !== 0) {
      code = (code << 8) | this.next8()
    }
    return String.fromCharCode(code)
  }

  align0x04(): void {
    const remainder = this.position % 0x04
    if (remainder !== 0) {
      this.position += 0x04 - remainder
    }
  }

  align0x10(): void {
    const remainder = this.position % 0x10
    if (remainder !== 0) {
      this.position += 0x10 - remainder
    }
  }

  offsetFrom(sectionHeader: SectionOffsetSource, offset: number): void {
    this.position = sectionHeader.sectionStartPosition + offset
  }

  offsetFromDataStart(sectionHeader: SectionOffsetSource, offset = 0): void {
    this.position = sectionHeader.dataStartPosition + offset
  }

  wrapped<T>(fn: () => T): T {
    const start = this.position
    const result = fn()
    this.position = start
    return result
  }

  hasMore(): boolean {
    return this.position < this.bytes.length
  }

  nextZeroTerminatedString(): string {
    let result = ''
    while (true) {
      const next = this.nextCharJis()
      if (next === '\u0000') {
        return result
      }
      result += next
    }
  }

  rotateRight(amount: number): void {
    for (let i = 0; i < this.bytes.length; i += 1) {
      this.bytes[i] = rotateRight8(this.bytes[i] ?? 0, amount)
    }
  }

  xorNext(mask: number): void {
    this.bytes[this.position] = (this.bytes[this.position] ?? 0) ^ (mask & 0xff)
    this.position += 1
  }

  swapNext8(offset: number, repetitions = 1): void {
    for (let i = 0; i < repetitions; i += 1) {
      const value = this.bytes[this.position] ?? 0
      this.bytes[this.position] = this.bytes[this.position + offset] ?? 0
      this.bytes[this.position + offset] = value
      this.position += 1
    }
  }

  rotateNext8(amount: number): void {
    this.bytes[this.position] = rotateRight8(this.bytes[this.position] ?? 0, amount)
    this.position += 1
  }

  xorNext8(mask: number): void {
    this.bytes[this.position] = (this.bytes[this.position] ?? 0) ^ (mask & 0xff)
    this.position += 1
  }

  write16(value: number): void {
    this.write8(value & 0xff)
    this.write8((value >>> 8) & 0xff)
  }

  write(...values: number[]): void {
    for (const value of values) {
      this.write8(value)
    }
  }

  write8(value: number): void {
    this.bytes[this.position] = value & 0xff
    this.position += 1
  }

  toString(): string {
    const pos = this.position.toString(16)
    const size = this.bytes.length.toString(16)
    return `[${this.resourceName}] Pos: ${pos} | Size: ${size}`
  }
}
