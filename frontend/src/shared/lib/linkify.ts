// 説明文を「ふつうの文字」と「URL」に分ける（要件 C-02・5.2 XSS 対策）
// http:// と https:// で始まる部分だけを URL とする（javascript: などはリンクにしない）
// 画面では、URL の部分だけを <a> にして表示する。HTML の文字列は作らない

export type TextPart = {
  text: string
  isUrl: boolean
}

export function linkify(text: string): TextPart[] {
  return (
    text
      .split(/(https?:\/\/[^\s]+)/g)
      // split に () を付けると、URL の部分は奇数番目に入る
      .map((part, i) => ({ text: part, isUrl: i % 2 === 1 }))
      .filter((part) => part.text !== '')
  )
}
