import { linkify } from '../lib/linkify.ts'

// 文字の中の http:// と https:// で始まる部分だけを、新しいタブで開くリンクにして表示する
// HTML の文字列は作らないので、説明文に <script> などが入っていても、ただの文字として出る（XSS 対策）
export function LinkifiedText({ text, linkClassName }: { text: string; linkClassName?: string }) {
  return (
    <>
      {linkify(text).map((part, i) =>
        part.isUrl ? (
          <a
            key={i}
            className={linkClassName}
            href={part.text}
            target="_blank"
            rel="noopener noreferrer"
            // リンクを押したときに、まわり（カードを開く・説明を編集する）まで反応しないようにする
            onClick={(e) => e.stopPropagation()}
          >
            {part.text}
          </a>
        ) : (
          part.text
        ),
      )}
    </>
  )
}
