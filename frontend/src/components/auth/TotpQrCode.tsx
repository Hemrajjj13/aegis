import React, { useEffect, useState } from 'react';
import QRCode from 'qrcode';
import { Copy, Check } from 'lucide-react';

interface TotpQrCodeProps {
  secret: string;
  otpauthUri: string;
}

export const TotpQrCode: React.FC<TotpQrCodeProps> = ({ secret, otpauthUri }) => {
  const [dataUrl, setDataUrl] = useState<string>('');
  const [copied, setCopied] = useState<boolean>(false);
  const [qrError, setQrError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;
    if (!otpauthUri) return;

    QRCode.toDataURL(otpauthUri, {
      width: 200,
      margin: 2,
      color: {
        dark: '#000000',
        light: '#FFFFFF',
      },
    })
      .then((url) => {
        if (isMounted) {
          setDataUrl(url);
          setQrError(null);
        }
      })
      .catch(() => {
        if (isMounted) {
          setQrError('Failed to generate local QR code.');
        }
      });

    return () => {
      isMounted = false;
    };
  }, [otpauthUri]);

  const handleCopySecret = async () => {
    try {
      await navigator.clipboard.writeText(secret);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Fallback
    }
  };

  return (
    <div className="flex flex-col items-center gap-4 py-2 w-full">
      <div className="p-3 bg-white rounded-md border border-border shadow-sm flex items-center justify-center">
        {qrError ? (
          <div role="alert" className="text-xs text-decline p-4 text-center">
            {qrError}
          </div>
        ) : dataUrl ? (
          <img
            src={dataUrl}
            alt="TOTP Authenticator QR Code"
            width={180}
            height={180}
            className="block"
          />
        ) : (
          <div className="w-[180px] h-[180px] flex items-center justify-center text-xs text-slate-400">
            Generating QR...
          </div>
        )}
      </div>

      <div className="w-full flex flex-col gap-1.5">
        <label htmlFor="manual-totp-key" className="text-xs font-medium text-text-muted">
          Manual Setup Key
        </label>
        <div className="flex items-center gap-2">
          <input
            id="manual-totp-key"
            type="text"
            readOnly
            value={secret}
            className="w-full font-mono text-xs px-3 py-2 rounded border border-border bg-surface-raised text-text select-all focus:outline-none"
          />
          <button
            type="button"
            onClick={handleCopySecret}
            className="px-3 py-2 text-xs font-medium rounded border border-border bg-surface hover:bg-surface-raised text-text flex items-center gap-1.5 transition-colors shrink-0 focus:outline-none focus:ring-1 focus:ring-accent"
            aria-label="Copy manual setup key"
          >
            {copied ? (
              <>
                <Check className="w-3.5 h-3.5 text-approve" aria-hidden="true" />
                <span className="text-approve">Copied</span>
              </>
            ) : (
              <>
                <Copy className="w-3.5 h-3.5" aria-hidden="true" />
                <span>Copy</span>
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
};
