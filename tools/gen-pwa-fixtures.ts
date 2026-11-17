// Regenerates the PWA contract fixtures in domain/src/test/resources/pwa from the PWA's own code.
// Usage, from the PWA repo:  PWA=$PWD OUT=<android-repo>/domain/src/test/resources/pwa \
//   sh -c 'envsubst "\$PWA \$OUT" < <android-repo>/tools/gen-pwa-fixtures.ts > /tmp/gen.ts && npx vite-node /tmp/gen.ts'
import 'fake-indexeddb/auto';
import { writeFileSync } from 'node:fs';
import { SEED_INVENTORY } from '${PWA}/src/paints/seed';
import { addPaint } from '${PWA}/src/paints/inventory';
import { buildAdvicePackage, buildBrainInstructions } from '${PWA}/src/advice/package';
import { buildImagePrompt } from '${PWA}/src/advice/image-prompt';
import { parseAdviceResponse } from '${PWA}/src/advice/parse';
import { openIndexedDbRepository } from '${PWA}/src/storage/indexeddb';
import { exportAll } from '${PWA}/src/storage/backup';
import { createProject, addProgressPhoto, addAdvice, setLook, setNotes, setZoneDone } from '${PWA}/src/projects/project';
import { BASE_SETTINGS, PAINTING_STYLES } from '${PWA}/src/projects/style';
import { buildScanPrompt } from '${PWA}/src/scan/scan';

const out = '${OUT}';
const photo = new Blob(['x'], { type: 'image/jpeg' });
const w = (name: string, text: string) => writeFileSync(`${out}/${name}`, text);
w('prompt-start.txt', buildAdvicePackage({ moment: 'start' }, SEED_INVENTORY, photo).text);
w('prompt-primed.txt', buildAdvicePackage({ moment: 'primed' }, SEED_INVENTORY, photo).text);
w('prompt-half-painted.txt', buildAdvicePackage({ moment: 'half-painted' }, SEED_INVENTORY, photo).text);
w('prompt-during.txt', buildAdvicePackage({ moment: 'during', zone: 'capa', question: 'highlight', freeText: 'borde dorado' }, SEED_INVENTORY, photo).text);
w('prompt-during-bare.txt', buildAdvicePackage({ moment: 'during' }, SEED_INVENTORY, photo).text);
w('brain-instructions.txt', buildBrainInstructions(SEED_INVENTORY));
w('scan-prompt.txt', buildScanPrompt());

const V = (code: string) => ({ brand: 'Vallejo', code });
const recipe = {
  version: 1 as const, kind: 'recipe' as const, moment: 'start' as const, prep: ['Imprimar en negro'], tips: ['Capas finas'],
  zones: [
    { name: 'túnica', technique: 'slapchop' as const, paint: V('72.032'), dilution: '1:3', steps: ['Aplicar'], brush: 'B02001' },
    { name: 'cara', technique: 'bsl' as const, bsl: { base: V('72.099'), shadow: V('72.071'), light: V('72.098') }, steps: [] },
    { name: 'peana', technique: 'slapchop' as const, paint: { brand: 'Citadel', code: 'Abaddon Black' }, dilution: '1:1', steps: [] },
  ],
};
w('image-prompt.txt', buildImagePrompt(recipe, SEED_INVENTORY));

// Painting styles: every style and every base setting, one prompt each, separated by a line of dashes.
const empty = { paints: [], brushes: [] };
const looks = [...PAINTING_STYLES.map((style) => ({ style })), ...BASE_SETTINGS.map((baseSetting) => ({ baseSetting })), { style: 'osl' as const, baseSetting: 'winter' as const }];
w('prompt-looks.txt', looks.map((look) => buildAdvicePackage({ moment: 'start', ...look }, empty, photo).text).join('\n-----\n'));
w('image-prompt-looks.txt', looks.map((look) => buildImagePrompt(recipe, SEED_INVENTORY, look)).join('\n-----\n'));

// Backup exported by the real PWA code, with every optional field exercised.
const repo = await openIndexedDbRepository('fixture');
const inv = addPaint({ ...SEED_INVENTORY, paints: SEED_INVENTORY.paints.slice(0, 2) },
  { brand: 'Citadel', code: 'Abaddon Black', name: 'Abaddon Black', range: 'Base', type: 'opaque', hex: '#231f20', family: { name: 'Custom', order: 4, role: 'shadow' } });
await repo.saveInventory({ ...inv, paints: [...inv.paints, { brand: 'Vallejo', code: '73.200', name: 'Sepia Wash', range: 'Wash', type: 'wash', hex: '#5a3d2b' }] });
const a = await repo.putBlob(new Blob([new Uint8Array([0xff, 0xd8, 0xff, 0x00, 0x01, 0xfe])], { type: 'image/jpeg' }), 'photo-initial');
const b = await repo.putBlob(new Blob([new Uint8Array([1, 2, 3])], { type: 'image/png' }), 'photo-target');
const c = await repo.putBlob(new Blob([new Uint8Array([9, 8, 7, 6])], { type: 'image/jpeg' }), 'photo-progress');
const parsed = parseAdviceResponse(JSON.stringify(recipe), inv);
if (!parsed.ok) throw new Error(parsed.errors.join());
const consult = parseAdviceResponse(JSON.stringify({ version: 1, kind: 'consultation', zone: 'capa', question: 'highlight', technique: 'Luz de filo', steps: ['Pincel seco'], paints: [{ paint: V('72.003'), dilution: '1:2' }], brush: 'X999', techniqueCard: 'drybrush' }), inv);
if (!consult.ok) throw new Error(consult.errors.join());
const primed = parseAdviceResponse(JSON.stringify({ ...recipe, moment: 'primed', prep: [] }), inv);
if (!primed.ok) throw new Error(primed.errors.join());
let p = createProject('Paladín', new Date('2026-11-10T09:00:00.000Z'), 'project-1');
p = { ...setNotes(p, 'Capa verde'), initialPhotoBlobId: a, targetImageBlobId: b };
p = addProgressPhoto(p, { id: 'pp-1', takenAt: '2026-11-11T10:00:00.000Z', blobId: c });
p = addAdvice(p, { id: 'adv-1', receivedAt: '2026-11-10T09:30:00.000Z', ...parsed.entry });
p = addAdvice(p, { id: 'adv-2', receivedAt: '2026-11-11T11:00:00.000Z', ...consult.entry });
p = addAdvice(p, { id: 'adv-3', receivedAt: '2026-11-11T12:00:00.000Z', ...primed.entry });
// Zones ticked off as painted on the first recipe; the other recipe has none (doneZones absent).
p = setZoneDone(setZoneDone(p, 'adv-1', 2, true), 'adv-1', 0, true);
await repo.saveProject(p);
// The second project has a painting style and a base setting; the first has none (both absent).
await repo.saveProject(setLook({ ...createProject('Goblin', new Date('2026-11-12T08:00:00.000Z'), 'project-2'), status: 'done' }, { style: 'nmm', baseSetting: 'desert' }));
const backup = await exportAll(repo, new Date('2026-11-14T12:00:00.000Z'));
w('backup-v1.json', JSON.stringify(backup, null, 2));
console.log('ok');
