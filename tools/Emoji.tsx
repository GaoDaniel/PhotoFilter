import React from "react";


function MyComponent(props: { className?: string | undefined; label?: string | undefined; symbol: number; }){
    return (<span className={props.className} role="img" aria-label={props.label}>
    {String.fromCodePoint(props.symbol)}
</span>);
}
function areEqual(prevProps: { symbol: any; }, nextProps: { symbol: any; }){
    return prevProps.symbol === nextProps.symbol;
}
export default React.memo(MyComponent, areEqual);

