import { describe, expect, it } from 'vitest'

import { DatParser } from '~/lib/resource/datParser'
import { DatId, InfoResource } from '~/lib/resource/datResource'

describe('DatParser', () => {
  it('parses directory + info sections into a directory tree', () => {
    const bytes = new Uint8Array([
      ...sectionHeader('main', 0x01, 0x10),
      ...sectionHeader('info', 0x45, 0x20),
      0x00,
      0x0a,
      0x03,
      0x02,
      0x04,
      0x00,
      0x0f,
      0x00,
      0x00,
      0x00,
      0x90,
      0x80,
      0x00,
      0x00,
      0x01,
      0x00,
      ...sectionHeader('done', 0x00, 0x10),
    ])

    const root = DatParser.parse('sample.dat', bytes)
    const info = root.getChildAs(DatId.info, InfoResource)

    expect(root.id).toEqual(new DatId('main'))
    expect(info.infoDefinition.movementChar).toBe('a')
    expect(info.infoDefinition.shakeFactor).toBe(3)
    expect(info.infoDefinition.rangeType).toBe('Wind')
    expect(info.infoDefinition.scale).toBe(0x90)
    expect(info.mountDefinition.poseType).toBe(0)
  })

  it('skips unknown sections and continues parsing later known sections', () => {
    const bytes = new Uint8Array([
      ...sectionHeader('main', 0x01, 0x10),
      ...sectionHeader('unkn', 0x07, 0x10),
      ...sectionHeader('info', 0x45, 0x20),
      0x00,
      0x0a,
      0x03,
      0x02,
      0x04,
      0x00,
      0x0f,
      0x00,
      0x00,
      0x00,
      0x90,
      0x80,
      0x00,
      0x00,
      0x01,
      0x00,
      ...sectionHeader('done', 0x00, 0x10),
    ])

    const root = DatParser.parse('sample-unknown.dat', bytes)
    const info = root.getChildAs(DatId.info, InfoResource)

    expect(root.id).toEqual(new DatId('main'))
    expect(info.infoDefinition.movementChar).toBe('a')
  })
})

function sectionHeader(id: string, sectionType: number, sectionSize: number): number[] {
  const sizeUnits = sectionSize >>> 4
  const meta = (sizeUnits << 7) | sectionType
  return [
    id.charCodeAt(0),
    id.charCodeAt(1),
    id.charCodeAt(2),
    id.charCodeAt(3),
    meta & 0xff,
    (meta >>> 8) & 0xff,
    (meta >>> 16) & 0xff,
    (meta >>> 24) & 0xff,
    0,
    0,
    0,
    0,
    0,
    0,
    0,
    0,
  ]
}
