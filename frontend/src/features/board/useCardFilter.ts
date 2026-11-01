import { useCallback, useMemo } from 'react'
import { useSearchParams } from 'react-router'
import type { LabelResponse } from '../../api/types.ts'
import { parseFilter, toSearchParams, withKnownLabels, type CardFilter } from './cardFilter.ts'

// 検索・絞り込みの条件を、URL（?q=…&label=…）から読み書きする（要件 S-04、詳細設計書 4.8）
// 検索欄（CardSearch）とボード（BoardPage）の両方がこれを使うので、どちらも同じ条件を見る
// labels（ボードのラベル）を渡すと、ボードにないラベルを条件から外したものを返す。
// 外した条件は、次に条件を変えたとき（setFilter）に URL からも消える
// 書き換えるときは replace にして、1文字入力するたびに「戻る」の履歴が増えないようにする
export function useCardFilter(labels?: LabelResponse[]) {
  const [params, setParams] = useSearchParams()
  const filter = useMemo(() => {
    const parsed = parseFilter(params)
    return labels ? withKnownLabels(parsed, labels) : parsed
  }, [params, labels])

  const setFilter = useCallback((next: CardFilter) => setParams(toSearchParams(next), { replace: true }), [setParams])
  const clear = useCallback(() => setParams(new URLSearchParams(), { replace: true }), [setParams])

  return { filter, setFilter, clear }
}
