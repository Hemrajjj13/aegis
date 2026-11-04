/**
 * Helper to generate RFC 3548 / RFC 6238 Base32 secret keys for TOTP setup in browser.
 */
const BASE32_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

export function generateBase32Secret(byteLength = 20): string {
  const bytes = new Uint8Array(byteLength);
  window.crypto.getRandomValues(bytes);

  let bits = '';
  for (let i = 0; i < bytes.length; i++) {
    bits += bytes[i].toString(2).padStart(8, '0');
  }

  let base32 = '';
  for (let i = 0; i + 5 <= bits.length; i += 5) {
    const chunk = bits.substring(i, i + 5);
    const index = parseInt(chunk, 2);
    base32 += BASE32_ALPHABET[index];
  }

  return base32;
}

export function buildOtpAuthUri(secret: string, email: string, issuer = 'Aegis'): string {
  const label = encodeURIComponent(`${issuer}:${email}`);
  const encIssuer = encodeURIComponent(issuer);
  return `otpauth://totp/${label}?secret=${secret}&issuer=${encIssuer}&algorithm=SHA1&digits=6&period=30`;
}

