import { DatId, DirectoryResource, type DatEntry } from '~/lib/resource/datResource'

type DatConstructor<T extends DatEntry> = new (...args: never[]) => T

export function collectByTypeRecursive<T extends DatEntry>(
  root: DirectoryResource,
  type: DatConstructor<T>,
): T[] {
  const entries: T[] = []
  const stack: DirectoryResource[] = [root]

  while (stack.length > 0) {
    const next = stack.pop()
    if (!next) {
      continue
    }

    entries.push(...next.collectByType(type))
    stack.push(...next.getSubDirectories())
  }

  return entries
}

export function getNullableChildRecursivelyAs<T extends DatEntry>(
  root: DirectoryResource,
  childId: DatId,
  type: DatConstructor<T>,
): T | null {
  const local = root.getNullableChildAs(childId, type)
  if (local) {
    return local
  }

  for (const child of root.getSubDirectories()) {
    const match = getNullableChildRecursivelyAs(child, childId, type)
    if (match) {
      return match
    }
  }

  return null
}
