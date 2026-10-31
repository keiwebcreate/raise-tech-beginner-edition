import { describe, expect, it } from 'vitest'
import { linkify } from './linkify.ts'

describe('linkify', () => {
  it('URL のない文はそのまま1つ', () => {
    expect(linkify('ふつうの文')).toEqual([{ text: 'ふつうの文', isUrl: false }])
  })

  it('http:// と https:// で始まる部分を URL として分ける', () => {
    expect(linkify('詳しくは https://example.com/a?b=1 を見る')).toEqual([
      { text: '詳しくは ', isUrl: false },
      { text: 'https://example.com/a?b=1', isUrl: true },
      { text: ' を見る', isUrl: false },
    ])
  })

  it('URL は空白か改行で終わる', () => {
    expect(linkify('http://a.example\n次の行')).toEqual([
      { text: 'http://a.example', isUrl: true },
      { text: '\n次の行', isUrl: false },
    ])
  })

  it('URL がいくつあっても分けられる', () => {
    const urls = linkify('https://a.example と https://b.example').filter((part) => part.isUrl)
    expect(urls.map((part) => part.text)).toEqual(['https://a.example', 'https://b.example'])
  })

  it('javascript: などはリンクにしない（XSS 対策）', () => {
    expect(linkify('javascript:alert(1)')).toEqual([{ text: 'javascript:alert(1)', isUrl: false }])
    expect(linkify('ftp://example.com')).toEqual([{ text: 'ftp://example.com', isUrl: false }])
  })

  it('URL のすぐ後ろに続く日本語は、リンクに含めない', () => {
    expect(linkify('詳しくはhttps://example.com/a?b=1を見る。')).toEqual([
      { text: '詳しくは', isUrl: false },
      { text: 'https://example.com/a?b=1', isUrl: true },
      { text: 'を見る。', isUrl: false },
    ])
    expect(linkify('（https://a.example）')).toEqual([
      { text: '（', isUrl: false },
      { text: 'https://a.example', isUrl: true },
      { text: '）', isUrl: false },
    ])
  })

  it('URL に使える記号（% など）は、URL に含める', () => {
    const url = 'https://example.com/path_1/~user/a-b.c?q=%E3%81%82&x=1#top'
    expect(linkify(url)).toEqual([{ text: url, isUrl: true }])
  })

  it('HTML のタグは、ただの文字として扱う', () => {
    expect(linkify('<b>太字</b>')).toEqual([{ text: '<b>太字</b>', isUrl: false }])
  })
})
