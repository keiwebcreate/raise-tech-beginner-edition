// 説明文を「ふつうの文字」と「URL」に分ける（要件 C-02・5.2 XSS 対策）
// http:// と https:// で始まる部分だけを URL とする（javascript: などはリンクにしない）
// 画面では、URL の部分だけを <a> にして表示する。HTML の文字列は作らない
// URL に使える文字（RFC 3986：半角の英数字と - . _ ~ : / ? # [ ] @ ! $ & ' ( ) * + , ; = %）が続く間を URL とする
// 「https://example.comを見て」のように、すぐ後ろに日本語が続いても、日本語はリンクに含めない
const URL_PATTERN = /(https?:\/\/[A-Za-z0-9\-._~:/?#[\]@!$&'()*+,;=%]+)/g

export type TextPart = {
  text: string
  isUrl: boolean
}

export function linkify(text: string): TextPart[] {
  return (
    text
      .split(URL_PATTERN)
      // split に () を付けると、URL の部分は奇数番目に入る
      .map((part, i) => ({ text: part, isUrl: i % 2 === 1 }))
      .filter((part) => part.text !== '')
  )
}
