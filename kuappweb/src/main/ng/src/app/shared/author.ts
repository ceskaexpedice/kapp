

export interface Author {
  full: string, 
  family: string, 
  given: string, 
  date: string,
  date_death: number,
  role: string[],
  termsOfAddress?: string ,
  nameIdentifier?: string,
  authorsId: string,
  isLast: boolean
}