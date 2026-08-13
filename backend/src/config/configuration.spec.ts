import { parseDeviceKeys } from './configuration';

const VALID_KEY = 'a'.repeat(24);

describe('API_KEYS', () => {
  it('liest ein einzelnes Geraet', () => {
    expect(parseDeviceKeys(`ff-kuehwiesen-sms:${VALID_KEY}`)).toEqual([
      { deviceId: 'ff-kuehwiesen-sms', key: VALID_KEY },
    ]);
  });

  it('liest mehrere Geraete', () => {
    const keys = parseDeviceKeys(`geraet-a:${VALID_KEY},geraet-b:${'b'.repeat(20)}`);
    expect(keys).toHaveLength(2);
    expect(keys[1].deviceId).toBe('geraet-b');
  });

  it('erlaubt Doppelpunkte im Schluessel', () => {
    const key = `${VALID_KEY}:mit:doppelpunkt`;
    expect(parseDeviceKeys(`geraet:${key}`)).toEqual([{ deviceId: 'geraet', key }]);
  });

  it('startet nicht ohne Konfiguration', () => {
    // Lieber gar nicht starten als versehentlich ohne Authentifizierung.
    expect(() => parseDeviceKeys(undefined)).toThrow(/nicht gesetzt/);
    expect(() => parseDeviceKeys('')).toThrow(/nicht gesetzt/);
  });

  it('weist zu kurze Schluessel ab', () => {
    expect(() => parseDeviceKeys('geraet:kurz')).toThrow(/zu kurz/);
  });

  it('weist Eintraege ohne Trennzeichen ab', () => {
    expect(() => parseDeviceKeys('nur-eine-kennung')).toThrow(/Ungueltiger Eintrag/);
  });

  it('weist doppelte Geraete-Kennungen ab', () => {
    expect(() => parseDeviceKeys(`geraet:${VALID_KEY},geraet:${'c'.repeat(20)}`)).toThrow(
      /mehrfach/,
    );
  });
});
