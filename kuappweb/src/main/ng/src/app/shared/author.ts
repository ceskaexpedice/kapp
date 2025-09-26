

export interface Author {
  full: string, 
  family: string, 
  given: string, 
  date: string,
  role: string[],
  termsOfAddress?: string ,
  nameIdentifier?: string,
  isLast: boolean
}