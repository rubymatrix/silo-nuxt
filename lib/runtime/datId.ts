import { DatId } from '~/lib/resource/datResource'

export function datIdFinalDigit(datId: DatId): number | null {
  const value = datId.id.at(-1)
  if (!value) {
    return null
  }

  const code = value.toLowerCase().charCodeAt(0)
  if (code >= 48 && code <= 57) {
    return code - 48
  }
  if (code >= 97 && code <= 122) {
    return 10 + (code - 97)
  }

  return null
}

export function datIdParameterizedMatch(id: DatId, pattern: DatId): boolean {
  if (id.id.length !== pattern.id.length) {
    return false
  }

  for (let i = 0; i < id.id.length; i += 1) {
    const c = pattern.id[i]
    if (c !== '?' && c !== id.id[i]) {
      return false
    }
  }

  return true
}
